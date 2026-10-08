package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway runs as the schema owner (DB_MIGRATION_USERNAME / _PASSWORD, the
 * <env>/payment-request-to-pay-service/db-migration secret, mounted only by the Helm
 * migration Job) and grants the runtime role (DB_USERNAME) only what the
 * service needs. Without the migration credentials (local runs) it falls back
 * to the app's own.
 */
class FlywayRoleConfigurationTest {

    @Test
    void flywayUsesTheSchemaOwnerAndGrantsTheRuntimeRole() throws Exception {
        FlywayProperties flyway = flyway(Map.of(
            "DB_USERNAME", "payment_request_to_pay_app",
            "SPRING_DATASOURCE_PASSWORD", "runtime-secret",
            "DB_MIGRATION_USERNAME", "payment_request_to_pay_owner",
            "DB_MIGRATION_PASSWORD", "owner-secret"));

        assertThat(flyway.getUser()).isEqualTo("payment_request_to_pay_owner");
        assertThat(flyway.getPassword()).isEqualTo("owner-secret");
        assertThat(flyway.getPlaceholders()).containsEntry("runtime_role", "payment_request_to_pay_app");
    }

    @Test
    void withoutMigrationCredentialsFlywayFallsBackToTheAppCredentials() throws Exception {
        FlywayProperties flyway = flyway(Map.of("SPRING_DATASOURCE_PASSWORD", "runtime-secret"));

        assertThat(flyway.getUser()).isEqualTo("payment_request_to_pay_app");
        assertThat(flyway.getPassword()).isEqualTo("runtime-secret");
        assertThat(flyway.getPlaceholders()).containsEntry("runtime_role", "payment_request_to_pay_app");
    }

    private static FlywayProperties flyway(Map<String, Object> podEnv) throws Exception {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("pod-env", podEnv));
        for (PropertySource<?> document : documents) {
            if (document.getProperty("spring.config.activate.on-profile") == null) {
                environment.getPropertySources().addAfter("pod-env", document);
            }
        }
        return Binder.get(environment).bind("spring.flyway", FlywayProperties.class).orElseGet(FlywayProperties::new);
    }
}
