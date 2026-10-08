package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * PostgreSQL for integration tests, from TEST_DB_URL / TEST_DB_USERNAME /
 * TEST_DB_PASSWORD (a service container in required-gates.yml). Locally the
 * tests are skipped without TEST_DB_URL; in CI (CI=true) they fail instead,
 * so a missing database can never turn the gate green.
 */
final class PostgresTestDatabase {

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
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> env("TEST_DB_USERNAME", "payment_test"));
        registry.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", "payment_test"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
