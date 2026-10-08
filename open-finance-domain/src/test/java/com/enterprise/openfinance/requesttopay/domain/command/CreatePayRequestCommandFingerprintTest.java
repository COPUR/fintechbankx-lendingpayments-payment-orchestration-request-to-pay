package com.enterprise.openfinance.requesttopay.domain.command;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CreatePayRequestCommandFingerprintTest {

    private static final Instant AT = Instant.parse("2026-02-10T10:00:00Z");

    @Test
    void sameBusinessPayloadGivesSameFingerprintRegardlessOfScaleTimeAndInteraction() {
        CreatePayRequestCommand first = new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co",
                new BigDecimal("500.00"), "AED", AT, "ix-1", "idem-1");
        CreatePayRequestCommand retry = new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co",
                new BigDecimal("500.0"), "aed", AT.plusSeconds(30), "ix-2", "idem-1");

        assertThat(first.fingerprint()).hasSize(64).isEqualTo(retry.fingerprint());
    }

    @Test
    void differentAmountCreditorOrDebtorChangesTheFingerprint() {
        String base = new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500.00"), "AED",
                AT, "ix", "k").fingerprint();

        assertThat(new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500.01"), "AED",
                AT, "ix", "k").fingerprint()).isNotEqualTo(base);
        assertThat(new CreatePayRequestCommand("TPP-001", "PSU-001", "Water Co", new BigDecimal("500.00"), "AED",
                AT, "ix", "k").fingerprint()).isNotEqualTo(base);
        assertThat(new CreatePayRequestCommand("TPP-001", "PSU-002", "Utilities Co", new BigDecimal("500.00"), "AED",
                AT, "ix", "k").fingerprint()).isNotEqualTo(base);
    }

    @Test
    void idempotencyKeyIsOptionalAndTrimmed() {
        assertThat(new CreatePayRequestCommand("TPP", "PSU", "C", BigDecimal.ONE, "AED", AT, "ix").idempotencyKey())
                .isNull();
        assertThat(new CreatePayRequestCommand("TPP", "PSU", "C", BigDecimal.ONE, "AED", AT, "ix", " k-1 ")
                .idempotencyKey()).isEqualTo("k-1");
    }
}
