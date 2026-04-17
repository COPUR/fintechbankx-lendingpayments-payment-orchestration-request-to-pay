package com.enterprise.openfinance.requesttopay.infrastructure.event;

import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestCreatedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestNotificationPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPayRequestNotificationAdapter implements PayRequestNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaPayRequestNotificationAdapter.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC_PAY_REQUESTS = "rtp.pay_requests.v1";

    public KafkaPayRequestNotificationAdapter(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void notifyPayRequestCreated(PayRequest request) {
        PayRequestCreatedEvent event = new PayRequestCreatedEvent(
                request.consentId(),
                request.creditorName(),
                request.amount(),
                request.currency(),
                request.psuId(),
                request.requestedAt()
        );
        sendEvent(request.consentId(), "PayRequestCreatedEvent", event);
    }

    @Override
    public void notifyPayRequestFinalized(PayRequest request) {
        if (request.status().name().equals("REJECTED")) {
            PayRequestRejectedEvent event = new PayRequestRejectedEvent(
                    request.consentId(),
                    request.updatedAt()
            );
            sendEvent(request.consentId(), "PayRequestRejectedEvent", event);
        } else if (request.status().name().equals("CONSUMED")) {
            PayRequestAcceptedEvent event = new PayRequestAcceptedEvent(
                    request.consentId(),
                    request.paymentIdOptional().orElse(null),
                    request.amount(),
                    request.currency(),
                    request.creditorName(),
                    request.psuId(),
                    request.updatedAt()
            );
            sendEvent(request.consentId(), "PayRequestAcceptedEvent", event);
        }
    }

    private void sendEvent(String aggregateId, String eventType, Object eventPayload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(eventPayload);
            kafkaTemplate.send(TOPIC_PAY_REQUESTS, aggregateId, payloadJson);
            log.info("Published event {} for aggregate {}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {} for aggregate {}", eventType, aggregateId, e);
            throw new RuntimeException("Serialization failure", e);
        }
    }
}
