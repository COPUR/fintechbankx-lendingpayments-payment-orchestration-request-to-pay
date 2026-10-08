package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * PostgreSQL for integration tests, from TEST_DB_URL / TEST_DB_USERNAME /
 * TEST_DB_PASSWORD (a service container in required-gates.yml). Locally the
 * tests are skipped without TEST_DB_URL; in CI (CI=true) they fail instead,
 * so a missing database can never turn the gate green.
 *
 * Runs the service with the two roles of production: Flyway as the schema
 * owner (the database's test user, through DB_MIGRATION_USERNAME / _PASSWORD,
 * in-process here, the Helm hook Job when deployed) and the application as a
 * separate runtime role (DB_USERNAME) with only the grants of V5. The owner
 * needs CREATEROLE to create that role.
 */
final class PostgresTestDatabase {

    static final String RUNTIME_ROLE = "payment_request_to_pay_runtime_it";
    static final String RUNTIME_PASSWORD = "payment_request_to_pay_runtime_it";

    private static boolean runtimeRoleReady;

    private PostgresTestDatabase() {
    }

    /** Call from a static @BeforeAll. */
    static void requireDatabase() {
        if (hasDatabase()) {
            return;
        }
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            Assertions.fail("CI=true but TEST_DB_URL is not set: PostgreSQL integration tests cannot run");
        }
        Assumptions.abort("Set TEST_DB_URL (and TEST_DB_USERNAME, TEST_DB_PASSWORD) to run PostgreSQL integration tests");
    }

    static boolean hasDatabase() {
        String url = System.getenv("TEST_DB_URL");
        return url != null && !url.isBlank();
    }

    static void register(DynamicPropertyRegistry registry) {
        if (!hasDatabase()) {
            return;
        }
        createRuntimeRole();
        registry.add("spring.datasource.url", PostgresTestDatabase::url);
        // Runtime role: what the pods connect as.
        registry.add("DB_USERNAME", () -> RUNTIME_ROLE);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        // Schema owner: what Flyway connects as.
        registry.add("DB_MIGRATION_USERNAME", PostgresTestDatabase::ownerUser);
        registry.add("DB_MIGRATION_PASSWORD", PostgresTestDatabase::ownerPassword);
    }

    static String url() {
        return System.getenv("TEST_DB_URL");
    }

    static String ownerUser() {
        return env("TEST_DB_USERNAME", "payment_test");
    }

    static String ownerPassword() {
        return env("TEST_DB_PASSWORD", "payment_test");
    }

    /** The schema owner's connection, for set-up and clean-up the runtime role may not do. */
    static JdbcTemplate owner() {
        return new JdbcTemplate(new DriverManagerDataSource(url(), ownerUser(), ownerPassword()));
    }

    /** A plain connection as the runtime role, outside the application. */
    static JdbcTemplate runtime() {
        createRuntimeRole();
        return new JdbcTemplate(new DriverManagerDataSource(url(), RUNTIME_ROLE, RUNTIME_PASSWORD));
    }

    private static synchronized void createRuntimeRole() {
        if (runtimeRoleReady) {
            return;
        }
        owner().execute("""
                do $$
                begin
                    if not exists (select 1 from pg_roles where rolname = '%1$s') then
                        create role %1$s login password '%2$s';
                    end if;
                end $$
                """.formatted(RUNTIME_ROLE, RUNTIME_PASSWORD));
        runtimeRoleReady = true;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
