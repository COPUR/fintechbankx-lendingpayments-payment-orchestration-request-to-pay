package com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PayRequestJpaEntityTest {

    @Test
    void constructor_shouldExposeAllValuesViaGetters() {
        Instant requestedAt = Instant.parse("2026-02-10T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-02-10T10:05:00Z");
        PayRequestJpaEntity entity = new PayRequestJpaEntity(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                "AWAITING_AUTHORISATION",
                requestedAt,
                updatedAt,
                "PAY-123"
        );

        assertThat(entity.getConsentId()).isEqualTo("CONS-001");
        assertThat(entity.getTppId()).isEqualTo("TPP-001");
        assertThat(entity.getPsuId()).isEqualTo("PSU-001");
        assertThat(entity.getCreditorName()).isEqualTo("Utilities Co");
        assertThat(entity.getAmount()).isEqualByComparingTo("500.00");
        assertThat(entity.getCurrency()).isEqualTo("AED");
        assertThat(entity.getStatus()).isEqualTo("AWAITING_AUTHORISATION");
        assertThat(entity.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(entity.getPaymentId()).isEqualTo("PAY-123");
    }
}
