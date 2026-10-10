package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Governance round 3 (2b): the service fails fast at startup unless transport encryption is
 * verified. The datasource URL must carry sslmode=verify-full and, when a Kafka client is
 * configured, the Kafka security.protocol must be SASL_SSL (SSL is accepted for the Strimzi
 * mutual-TLS profile). fintechbankx.tls.enforce is true by default in application.yml; only
 * the test resources and the local profile set it false, and the chart never does.
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

    @Test
    void onlyExplicitLocalOrTestConfigurationSwitchesItOff() {
        service.withPropertyValues(REQUIRE, "spring.kafka.security.protocol=PLAINTEXT", "fintechbankx.tls.enforce=false")
            .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void applicationYmlEnforcesByDefaultAndTheLocalProfileSwitchesItOff() throws IOException {
        assertThat(bind("application.yml", "fintechbankx.tls.enforce")).isEqualTo(Boolean.TRUE);
        assertThat(bind("application-local.yml", "fintechbankx.tls.enforce")).isEqualTo(Boolean.FALSE);
        // The deployed profiles never switch it off.
        assertThat(bind("application-kafka-msk.yml", "fintechbankx.tls.enforce")).isNull();
        assertThat(bind("application-kafka-strimzi.yml", "fintechbankx.tls.enforce")).isNull();
    }

    private static Boolean bind(String file, String property) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load(file, new ClassPathResource(file))
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
