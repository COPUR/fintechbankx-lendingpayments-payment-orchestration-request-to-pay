package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxPayRequestEventPublisherTest {

    @Test
    void writesOneOutboxRowPerEventWithTheCorrelationId() {
        SpringDataOutboxRepository outbox = mock(SpringDataOutboxRepository.class);
        OutboxPayRequestEventPublisher publisher = new OutboxPayRequestEventPublisher(outbox,
                new PayRequestEventEnvelopeFactory(new ObjectMapper()));
        PayRequest created = PayRequest.create("CONS-1", new CreatePayRequestCommand("TPP-001", "PSU-001",
                "Utilities Co", new BigDecimal("12.50"), "AED", Instant.parse("2026-02-10T10:00:00Z"), "ix"),
                Instant.parse("2026-02-10T10:00:01Z"));

        publisher.publish(created, created.domainEvents(), "ix-corr");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OutboxEventJpaEntity>> rows = ArgumentCaptor.forClass(List.class);
        verify(outbox).saveAll(rows.capture());
        assertThat(rows.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getCorrelationId()).isEqualTo("ix-corr");
            assertThat(row.getEventId()).isEqualTo(created.domainEvents().getFirst().eventId());
        });
    }

    @Test
    void capturesTheW3cTraceparentOfTheCurrentSpan() {
        org.slf4j.MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        org.slf4j.MDC.put("spanId", "00f067aa0ba902b7");
        try {
            assertThat(OutboxPayRequestEventPublisher.currentTraceparent())
                    .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            org.slf4j.MDC.put("spanId", "not-hex");
            assertThat(OutboxPayRequestEventPublisher.currentTraceparent()).isNull();
        } finally {
            org.slf4j.MDC.clear();
        }
        assertThat(OutboxPayRequestEventPublisher.currentTraceparent()).isNull();
    }
}
