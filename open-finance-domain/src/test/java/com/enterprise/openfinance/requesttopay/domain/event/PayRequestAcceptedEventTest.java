package com.enterprise.openfinance.requesttopay.domain.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PayRequestAcceptedEventTest {

    @Test
    void shouldBuildEventWithGeneratedIdAndDefaultVersion() {
        Instant occurredOn = Instant.parse("2026-02-10T10:15:00Z");

        PayRequestAcceptedEvent event = new PayRequestAcceptedEvent(
                "CONS-001",
                "PAY-123",
                new BigDecimal("500.00"),
                "AED",
                "Utilities Co",
                "PSU-001",
                occurredOn
        );

        assertThat(event.eventId()).isNotNull();
        assertThat(event.aggregateId()).isEqualTo("CONS-001");
        assertThat(event.paymentId()).isEqualTo("PAY-123");
        assertThat(event.amount()).isEqualByComparingTo("500.00");
        assertThat(event.currency()).isEqualTo("AED");
        assertThat(event.creditorName()).isEqualTo("Utilities Co");
        assertThat(event.debtorId()).isEqualTo("PSU-001");
        assertThat(event.occurredOn()).isEqualTo(occurredOn);
        assertThat(event.version()).isEqualTo(1);
    }
}
