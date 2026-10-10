package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deployed migration step: the Helm pre-install/pre-upgrade Job runs the
 * image with the argument "migrate". It runs Flyway as the schema owner,
 * grants the runtime role, starts no web server, no Kafka and no security,
 * and exits 0 after every migration (V1 to V7). Runs against a scratch schema so the shared one
 * is untouched. As in production (terraform-modules aurora-postgresql
 * role_bootstrap_sql), the DBA bootstrap creates the schema owned by the
 * migration role; Flyway never creates it (create-schemas false).
 */
class DatabaseMigrationIT {

    private static final String SCHEMA = "sc_pay_rtp_migration_job_it";

    @BeforeAll
    static void requireDatabase() {
        PostgresTestDatabase.requireDatabase();
    }

    @AfterEach
    void dropScratchSchema() {
        PostgresTestDatabase.owner().execute("drop schema if exists " + SCHEMA + " cascade");
    }

    @Test
    void migrateRunsFlywayAsTheOwnerGrantsTheRuntimeRoleAndExits() {
        PostgresTestDatabase.owner().execute("drop schema if exists " + SCHEMA + " cascade");
        PostgresTestDatabase.owner().execute("create schema " + SCHEMA); // the DBA bootstrap's job
        PostgresTestDatabase.runtime().queryForObject("select 1", Integer.class); // creates the runtime role

        int exitCode = RequestToPayApplication.run(
            "migrate",
            "--spring.datasource.url=" + PostgresTestDatabase.url(),
            "--DB_USERNAME=" + PostgresTestDatabase.RUNTIME_ROLE,
            "--DB_MIGRATION_USERNAME=" + PostgresTestDatabase.ownerUser(),
            "--DB_MIGRATION_PASSWORD=" + PostgresTestDatabase.ownerPassword(),
            "--spring.flyway.schemas=" + SCHEMA,
            "--spring.flyway.default-schema=" + SCHEMA);

        assertThat(exitCode).isZero();
        JdbcTemplate owner = PostgresTestDatabase.owner();
        assertThat(owner.queryForObject(
            "select count(*) from " + SCHEMA + ".flyway_schema_history where success", Integer.class)).isGreaterThanOrEqualTo(5);
        assertThat(owner.queryForObject(
            "select tableowner from pg_tables where schemaname = ? and tablename = 'pay_request'", String.class, SCHEMA))
            .isEqualTo(PostgresTestDatabase.ownerUser());
        assertThat(owner.queryForObject(
            "select has_table_privilege(?, ?, 'INSERT')", Boolean.class,
            PostgresTestDatabase.RUNTIME_ROLE, SCHEMA + ".pay_request")).isTrue();
        assertThat(owner.queryForObject(
            "select has_schema_privilege(?, ?, 'CREATE')", Boolean.class,
            PostgresTestDatabase.RUNTIME_ROLE, SCHEMA)).isFalse();
        // role_bootstrap_sql: the runtime role gets USAGE, SELECT on the schema's sequences.
        String sequence = owner.queryForObject(
            "select pg_get_serial_sequence(?, 'created_seq')", String.class, SCHEMA + ".outbox_event");
        assertThat(sequence).isNotNull();
        assertThat(owner.queryForObject("select has_sequence_privilege(?, ?, 'USAGE')", Boolean.class,
            PostgresTestDatabase.RUNTIME_ROLE, sequence)).isTrue();
        assertThat(owner.queryForObject("select has_sequence_privilege(?, ?, 'SELECT')", Boolean.class,
            PostgresTestDatabase.RUNTIME_ROLE, sequence)).isTrue();
        assertThat(owner.queryForObject("select has_sequence_privilege(?, ?, 'UPDATE')", Boolean.class,
            PostgresTestDatabase.RUNTIME_ROLE, sequence)).isFalse();
    }

    /** The schema is the DBA bootstrap's: without it the migration fails rather than creating it. */
    @Test
    void migrateDoesNotCreateTheSchema() {
        PostgresTestDatabase.owner().execute("drop schema if exists " + SCHEMA + " cascade");

        int exitCode = RequestToPayApplication.run(
            "migrate",
            "--spring.datasource.url=" + PostgresTestDatabase.url(),
            "--DB_USERNAME=" + PostgresTestDatabase.RUNTIME_ROLE,
            "--DB_MIGRATION_USERNAME=" + PostgresTestDatabase.ownerUser(),
            "--DB_MIGRATION_PASSWORD=" + PostgresTestDatabase.ownerPassword(),
            "--spring.flyway.schemas=" + SCHEMA,
            "--spring.flyway.default-schema=" + SCHEMA);

        assertThat(exitCode).isNotZero();
        assertThat(PostgresTestDatabase.owner().queryForObject(
            "select count(*) from pg_namespace where nspname = ?", Integer.class, SCHEMA)).isZero();
    }

    @Test
    void migrateFailsWithANonZeroExitCodeWhenTheDatabaseRefuses() {
        int exitCode = RequestToPayApplication.run(
            "migrate",
            "--spring.datasource.url=" + PostgresTestDatabase.url(),
            "--DB_USERNAME=" + PostgresTestDatabase.RUNTIME_ROLE,
            "--DB_MIGRATION_USERNAME=" + PostgresTestDatabase.ownerUser(),
            "--DB_MIGRATION_PASSWORD=wrong-password",
            "--spring.flyway.schemas=" + SCHEMA,
            "--spring.flyway.default-schema=" + SCHEMA);

        assertThat(exitCode).isNotZero();
    }

    /**
     * V7 (ADR-019 section 8, one topic per aggregate): rows written before it,
     * with whatever topic the old per-event mapping stored, now name the
     * aggregate topic evt.pay.rtp.v1 that the relay sends them to.
     */
    @Test
    void v7PointsEveryStoredRowAtTheAggregateTopic() {
        PostgresTestDatabase.owner().execute("drop schema if exists " + SCHEMA + " cascade");
        PostgresTestDatabase.owner().execute("create schema " + SCHEMA);
        flyway("6").migrate();
        JdbcTemplate owner = PostgresTestDatabase.owner();
        owner.update("insert into " + SCHEMA + ".outbox_event (event_id, aggregate_type, aggregate_id, aggregate_version,"
            + " event_type, topic, payload, correlation_id, occurred_at)"
            + " values ('5c1e2c4a-6a7b-4c1d-9e8f-0a1b2c3d4e5f', 'PayRequest', 'CONS-V7', 0, 'Payments.PayRequest.Created.v1',"
            + " 'evt.pay.rtp.written-before-v7', '{}'::jsonb, 'ix-v7', now())");

        flyway("latest").migrate();

        assertThat(owner.queryForObject("select topic from " + SCHEMA + ".outbox_event where aggregate_id = 'CONS-V7'",
            String.class)).isEqualTo("evt.pay.rtp.v1");
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(PostgresTestDatabase.url(), PostgresTestDatabase.ownerUser(), PostgresTestDatabase.ownerPassword())
            .schemas(SCHEMA)
            .defaultSchema(SCHEMA)
            .createSchemas(false)
            .locations("classpath:db/migration")
            .placeholders(Map.of("runtime_role", PostgresTestDatabase.ownerUser()))
            .target(target)
            .load();
    }
}
