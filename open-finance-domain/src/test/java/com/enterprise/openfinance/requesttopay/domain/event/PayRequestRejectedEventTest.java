package com.enterprise.openfinance.requesttopay.domain.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PayRequestRejectedEventTest {

    @Test
    void shouldBuildEventWithGeneratedIdAndDefaultVersion() {
        Instant occurredOn = Instant.parse("2026-02-10T10:20:00Z");

        PayRequestRejectedEvent event = new PayRequestRejectedEvent("CONS-001", "TPP-001", "debtor declined", occurredOn);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.aggregateId()).isEqualTo("CONS-001");
        assertThat(event.actorClientId()).isEqualTo("TPP-001");
        assertThat(event.reason()).isEqualTo("debtor declined");
        assertThat(event.occurredOn()).isEqualTo(occurredOn);
        assertThat(event.version()).isEqualTo(1);
    }
}
