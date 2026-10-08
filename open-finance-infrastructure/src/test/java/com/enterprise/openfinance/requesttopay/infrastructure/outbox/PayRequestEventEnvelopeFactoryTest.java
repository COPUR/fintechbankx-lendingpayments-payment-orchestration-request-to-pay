package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox row must match api/asyncapi/svc-pay-request-to-pay.yaml:
 * envelope fields, topic, eventType, decimal-string amounts.
 */
class PayRequestEventEnvelopeFactoryTest {

    private static final Instant REQUESTED = Instant.parse("2026-02-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-02-10T10:00:01Z");

    private final ObjectMapper json = new ObjectMapper();
    private final PayRequestEventEnvelopeFactory factory = new PayRequestEventEnvelopeFactory(json);

    @Test
    void createdEventBecomesTheCreatedEnvelope() throws Exception {
        PayRequest created = PayRequest.create("CONS-RTP-1", command(), NOW);

        OutboxEventJpaEntity row = factory.toOutboxRow(created, created.domainEvents().getFirst(), "ix-42");
        JsonNode envelope = json.readTree(row.getPayload());

        assertThat(row.getTopic()).isEqualTo("evt.pay.rtp.created.v1");
        assertThat(row.getEventType()).isEqualTo("Payments.PayRequest.Created.v1");
        assertThat(row.getAggregateType()).isEqualTo("PayRequest");
        assertThat(row.getAggregateId()).isEqualTo("CONS-RTP-1");
        assertThat(row.getAggregateVersion()).isZero();
        assertThat(row.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);
        assertThat(envelope.fieldNames()).toIterable().containsExactly("eventId", "eventType", "occurredAt",
                "aggregateId", "aggregateVersion", "correlationId", "causationId", "producer", "data");
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("occurredAt").asText()).isEqualTo("2026-02-10T10:00:01Z");
        assertThat(envelope.get("correlationId").asText()).isEqualTo("ix-42");
        assertThat(envelope.get("causationId").isNull()).isTrue();
        assertThat(envelope.get("producer").asText()).isEqualTo("svc-pay-request-to-pay");
        JsonNode data = envelope.get("data");
        assertThat(data.get("amount").isTextual()).isTrue();
        assertThat(data.get("amount").asText()).isEqualTo("500.00");
        assertThat(data.get("currency").asText()).isEqualTo("AED");
        assertThat(data.get("creditorName").asText()).isEqualTo("Utilities Co");
        assertThat(data.get("debtorId").asText()).isEqualTo("PSU-001");
    }

    @Test
    void acceptedEnvelopeCarriesPaymentIdAndVersionOne() throws Exception {
        PayRequest accepted = PayRequest.create("CONS-RTP-1", command(), NOW).consume("PAY-9", new DecisionBy("TPP-001", null), NOW.plusSeconds(60));

        OutboxEventJpaEntity row = factory.toOutboxRow(accepted, accepted.domainEvents().getFirst(), "ix-43");
        JsonNode envelope = json.readTree(row.getPayload());

        assertThat(row.getTopic()).isEqualTo("evt.pay.rtp.accepted.v1");
        assertThat(envelope.get("eventType").asText()).isEqualTo("Payments.PayRequest.Accepted.v1");
        assertThat(envelope.get("aggregateVersion").asLong()).isEqualTo(1);
        assertThat(envelope.get("data").get("paymentId").asText()).isEqualTo("PAY-9");
        assertThat(envelope.get("data").get("amount").asText()).isEqualTo("500.00");
        assertThat(envelope.get("data").get("actorClientId").asText()).isEqualTo("TPP-001");
        assertThat(envelope.get("data").has("reason")).isFalse();
    }

    @Test
    void rejectedEnvelopeNamesTheDecidingClientAndTheReason() throws Exception {
        PayRequest rejected = PayRequest.create("CONS-RTP-1", command(), NOW)
                .reject(new DecisionBy("TPP-001", "debtor declined"), NOW.plusSeconds(60));

        OutboxEventJpaEntity row = factory.toOutboxRow(rejected, rejected.domainEvents().getFirst(), "ix-44");
        JsonNode envelope = json.readTree(row.getPayload());

        assertThat(row.getTopic()).isEqualTo("evt.pay.rtp.rejected.v1");
        assertThat(envelope.get("eventType").asText()).isEqualTo("Payments.PayRequest.Rejected.v1");
        assertThat(envelope.get("data").get("actorClientId").asText()).isEqualTo("TPP-001");
        assertThat(envelope.get("data").get("reason").asText()).isEqualTo("debtor declined");
        assertThat(envelope.get("data").size()).isEqualTo(2);
    }

    private static CreatePayRequestCommand command() {
        return new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500"), "AED",
                REQUESTED, "ix-1");
    }
}
