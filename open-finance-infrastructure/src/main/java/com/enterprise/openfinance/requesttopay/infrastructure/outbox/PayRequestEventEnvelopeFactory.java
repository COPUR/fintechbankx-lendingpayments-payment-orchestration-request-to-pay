package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestCreatedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestDomainEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns PayRequest domain events into the public envelope of the AsyncAPI
 * contract api/asyncapi/svc-pay-request-to-pay.yaml: topic
 * evt.pay.rtp.&lt;event&gt;.v1, eventType Payments.PayRequest.&lt;Event&gt;.v1,
 * amounts as decimal strings, record key = aggregateId (consentId).
 */
public class PayRequestEventEnvelopeFactory {

    public static final String PRODUCER = "svc-pay-request-to-pay";
    public static final String AGGREGATE_TYPE = "PayRequest";
    public static final String TOPIC_PREFIX = "evt.pay.rtp.";

    private final ObjectMapper objectMapper;

    public PayRequestEventEnvelopeFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEventJpaEntity toOutboxRow(PayRequest payRequest, PayRequestDomainEvent event, String correlationId) {
        PublicEvent mapped = map(event);
        String aggregateId = payRequest.consentId();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", mapped.eventType());
        envelope.put("occurredAt", event.occurredOn().toString());
        envelope.put("aggregateId", aggregateId);
        envelope.put("aggregateVersion", payRequest.version());
        envelope.put("correlationId", correlationId);
        envelope.put("causationId", null);
        envelope.put("producer", PRODUCER);
        envelope.put("data", mapped.data());

        return new OutboxEventJpaEntity(event.eventId(), AGGREGATE_TYPE, aggregateId, payRequest.version(),
                mapped.eventType(), mapped.topic(), toJson(envelope), correlationId, event.occurredOn());
    }

    static PublicEvent map(PayRequestDomainEvent event) {
        return switch (event) {
            case PayRequestCreatedEvent e -> new PublicEvent("created", "Created", data(
                    "creditorName", e.creditorName(),
                    "amount", decimal(e.amount()),
                    "currency", e.currency(),
                    "debtorId", e.debtorId()));
            case PayRequestAcceptedEvent e -> new PublicEvent("accepted", "Accepted", data(
                    "paymentId", e.paymentId(),
                    "amount", decimal(e.amount()),
                    "currency", e.currency(),
                    "creditorName", e.creditorName(),
                    "debtorId", e.debtorId()));
            case PayRequestRejectedEvent e -> new PublicEvent("rejected", "Rejected", data());
        };
    }

    private static String decimal(BigDecimal amount) {
        return amount.toPlainString();
    }

    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String toJson(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise pay request event envelope", e);
        }
    }

    record PublicEvent(String topicSuffix, String eventName, Map<String, Object> data) {
        String topic() {
            return TOPIC_PREFIX + topicSuffix + ".v1";
        }

        String eventType() {
            return "Payments.PayRequest." + eventName + ".v1";
        }
    }
}
