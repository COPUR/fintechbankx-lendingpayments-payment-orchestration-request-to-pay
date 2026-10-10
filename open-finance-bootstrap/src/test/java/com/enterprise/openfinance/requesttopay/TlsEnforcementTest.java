package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Governance round 3 (2b) and guardrail 4a: the service fails fast at startup unless transport
 * encryption is verified. Every datasource URL a pool can use (spring.datasource.url,
 * spring.datasource.hikari.jdbc-url, spring.flyway.url) is parsed the way PgJDBC parses it
 * (case-sensitive keys, last repeated value wins) and must carry exactly one sslmode=verify-full
 * and no verification-bypass parameter; when a Kafka client is configured, the producer's
 * effective security.protocol must be SASL_SSL (SSL is accepted for the Strimzi mutual-TLS
 * profile). fintechbankx.tls.enforce is true by default in application.yml; only the test
 * resources and an explicit local environment set it false; no packaged profile and never the chart.
 */
class TlsEnforcementTest {

    private static final String VERIFY_FULL =
        "spring.datasource.url=jdbc:postgresql://db.example.internal:5432/rtp?sslmode=verify-full&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem";
    private static final String REQUIRE =
        "spring.datasource.url=jdbc:postgresql://db.example.internal:5432/rtp?sslmode=require";

    /** The service: a datasource and a Kafka producer. */
    private final ApplicationContextRunner service = new ApplicationContextRunner()
        .withUserConfiguration(TlsEnforcement.class)
        .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class));

    @Test
    void failsOnDatasourceSslmodeRequire() {
        service.withPropertyValues(REQUIRE, "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
            assertThat(ctx).hasFailed();
            String message = failure(ctx);
            assertThat(message).contains("spring.datasource.url").contains("sslmode=require").contains("sslmode=verify-full");
            // the URL itself may carry a credential: the message never repeats it
            assertThat(message).doesNotContain("db.example.internal").doesNotContain("jdbc:postgresql");
        });
    }

    @Test
    void failsOnKafkaPlaintext() {
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=PLAINTEXT").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(failure(ctx)).contains("spring.kafka.security.protocol").contains("PLAINTEXT").contains("SASL_SSL");
        });
    }

    @Test
    void passesOnVerifyFullPlusSaslSsl() {
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(TlsEnforcement.class);
        });
    }

    /** Both settings wrong: one failure that names both, so an operator fixes them in one go. */
    @Test
    void namesEveryOffendingSettingAtOnce() {
        service.withPropertyValues(REQUIRE, "spring.kafka.security.protocol=SASL_PLAINTEXT").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(failure(ctx)).contains("spring.datasource.url").contains("spring.kafka.security.protocol")
                .contains("SASL_PLAINTEXT");
        });
    }

    @Test
    void aDatasourceUrlWithoutSslmodeFails() {
        service.withPropertyValues("spring.datasource.url=jdbc:postgresql://db.example.internal:5432/rtp",
            "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.url").contains("no sslmode").contains("sslmode=verify-full");
            });
    }

    /** Kafka defaults to PLAINTEXT when the protocol is not set at all. */
    @Test
    void anUnsetKafkaProtocolFails() {
        service.withPropertyValues(VERIFY_FULL).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(failure(ctx)).contains("spring.kafka.security.protocol").contains("not set").contains("SASL_SSL");
        });
    }

    /** spring.kafka.properties.* and the producer's own settings override the common protocol. */
    @Test
    void anOverrideToPlaintextThroughTheProducerPropertiesFails() {
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=SASL_SSL",
            "spring.kafka.producer.properties.security.protocol=PLAINTEXT").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.kafka.security.protocol").contains("PLAINTEXT");
            });
    }

    /** The kafka-strimzi profile authenticates with a client certificate over TLS (security.protocol SSL). */
    @Test
    void strimziMutualTlsIsAccepted() {
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=SSL")
            .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    /** The migration Job (RequestToPayApplication "migrate") configures no Kafka client: only the database is checked. */
    @Test
    void withoutAKafkaClientOnlyTheDatasourceIsChecked() {
        ApplicationContextRunner migration = new ApplicationContextRunner().withUserConfiguration(TlsEnforcement.class);

        migration.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=PLAINTEXT")
            .run(ctx -> assertThat(ctx).hasNotFailed());
        migration.withPropertyValues(REQUIRE).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(failure(ctx)).contains("spring.datasource.url").doesNotContain("spring.kafka");
        });
    }

    /** The migrate context imports the check explicitly: it is not component-scanned. */
    @Test
    void theMigrateContextFailsFastBeforeFlywayConnects() {
        new ApplicationContextRunner()
            .withUserConfiguration(RequestToPayApplication.DatabaseMigration.class)
            .withPropertyValues(REQUIRE, "spring.flyway.enabled=true")
            .run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.url").contains("sslmode=verify-full");
            });
    }

    /** spring.flyway.url, when set, replaces the datasource URL for migrations and is held to the same rule. */
    @Test
    void aFlywayUrlOfItsOwnIsCheckedToo() {
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=SASL_SSL",
            "spring.flyway.url=jdbc:postgresql://db.example.internal:5432/rtp?sslmode=require").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.flyway.url").contains("sslmode=require");
            });
    }

    /** Hikari prefers spring.datasource.hikari.jdbc-url once bound, so it is held to the same rule (round-5 minor). */
    @Test
    void theHikariJdbcUrlIsCheckedToo() {
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=SASL_SSL",
            "spring.datasource.hikari.jdbc-url=jdbc:postgresql://db.example.internal:5432/rtp?sslmode=require").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.hikari.jdbc-url").contains("sslmode=require");
            });
        // The environment-variable spelling binds to the same property (relaxed binding), so it is read by binding, not by name.
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=SASL_SSL")
            .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "probe-systemEnvironment", // Boot applies the env-var name mapping to sources named *systemEnvironment
                Map.of("SPRING_DATASOURCE_HIKARI_JDBCURL", "jdbc:postgresql://db.example.internal:5432/rtp?sslmode=disable"))))
            .run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.hikari.jdbc-url").contains("sslmode=disable");
            });
    }

    /**
     * PgJDBC keeps the last value of a repeated parameter: a URL that reads verify-full first and
     * disable last connects unencrypted. The URL is read the way the driver reads it.
     */
    @Test
    void aRepeatedSslmodeFailsEvenWhenTheFirstOneIsVerifyFull() {
        service.withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://db.example.internal:5432/rtp?sslmode=verify-full"
                + "&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem&sslmode=disable",
            "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.url").contains("sslmode 2 times")
                    .contains("sslmode=verify-full");
            });
    }

    @Test
    void aRepeatedSslrootcertFails() {
        service.withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://db.example.internal:5432/rtp?sslmode=verify-full"
                + "&sslrootcert=/tmp/decoy.pem&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem",
            "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.url").contains("sslrootcert 2 times");
            });
    }

    /** PgJDBC parameter names are case-sensitive: SSLMODE is ignored and the driver falls back to prefer. */
    @Test
    void anUpperCaseSslmodeIsNotReadByTheDriverAndFails() {
        service.withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://db.example.internal:5432/rtp?SSLMODE=verify-full"
                + "&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem",
            "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.url").contains("no sslmode").contains("SSLMODE")
                    .contains("case-sensitive");
            });
    }

    /** A value that merely contains the words is not an sslmode (applicationName=sslmode=verify-full). */
    @Test
    void anSslmodeHiddenInAnotherValueDoesNotCount() {
        service.withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://db.example.internal:5432/rtp?sslmode=require"
                + "&applicationName=sslmode=verify-full&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem",
            "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.datasource.url").contains("sslmode=require");
            });
    }

    /** These parameters replace or bypass certificate and host-name verification, whatever the sslmode. */
    @ParameterizedTest
    @ValueSource(strings = {"sslfactory=org.postgresql.ssl.NonValidatingFactory", "sslfactoryarg=x", "sslhostnameverifier=x.Y",
        "sslpasswordcallback=x.Y", "service=rtp", "SSLFACTORY=org.postgresql.ssl.NonValidatingFactory"})
    void verificationBypassParametersAreRefused(String parameter) {
        String key = parameter.substring(0, parameter.indexOf('='));
        service.withPropertyValues(VERIFY_FULL + "&" + parameter, "spring.kafka.security.protocol=SASL_SSL").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(failure(ctx)).contains("spring.datasource.url").contains(key).contains("verification");
        });
    }

    /** spring.kafka.properties.* (the common client map) overrides spring.kafka.security.protocol as well. */
    @Test
    void anOverrideToPlaintextThroughTheCommonKafkaPropertiesFails() {
        service.withPropertyValues(VERIFY_FULL, "spring.kafka.security.protocol=SASL_SSL",
            "spring.kafka.properties.security.protocol=PLAINTEXT").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(failure(ctx)).contains("spring.kafka.security.protocol").contains("PLAINTEXT");
            });
    }

    @Test
    void onlyExplicitLocalOrTestConfigurationSwitchesItOff() {
        service.withPropertyValues(REQUIRE, "spring.kafka.security.protocol=PLAINTEXT", "fintechbankx.tls.enforce=false")
            .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    /**
     * application.yml enforces by default; the local profile (regression's local parity runs) is the
     * only packaged profile that switches the assertion off, and the chart refuses every route to it
     * (profile names by regex, kafka.profile validation, JVM options: deployability.yml). Every other
     * packaged application-*.yml leaves the property alone.
     */
    @Test
    void onlyTheLocalProfileAmongThePackagedYmlsSwitchesTheAssertionOff() throws IOException {
        Resource[] packaged = new PathMatchingResourcePatternResolver().getResources("classpath*:application*.yml");
        assertThat(packaged).extracting(Resource::getFilename)
            .contains("application.yml", "application-local.yml", "application-kafka-msk.yml", "application-kafka-strimzi.yml");
        for (Resource file : packaged) {
            Boolean enforce = bind(file, "fintechbankx.tls.enforce");
            switch (file.getFilename()) {
                case "application.yml" -> assertThat(enforce).as("application.yml enforces by default").isEqualTo(Boolean.TRUE);
                case "application-local.yml" -> assertThat(enforce).as("the local profile switches it off").isEqualTo(Boolean.FALSE);
                default -> assertThat(enforce).as("%s must not set fintechbankx.tls.enforce", file.getFilename()).isNull();
            }
        }
    }

    private static Boolean bind(Resource file, String property) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load(file.getFilename(), file)
            .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment).bind(property, Boolean.class).orElse(null);
    }

    private static String failure(AssertableApplicationContext ctx) {
        Throwable failure = ctx.getStartupFailure();
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
