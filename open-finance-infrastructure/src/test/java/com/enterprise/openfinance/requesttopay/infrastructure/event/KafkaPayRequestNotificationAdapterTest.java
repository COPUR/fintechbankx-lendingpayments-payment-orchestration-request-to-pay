package com.enterprise.openfinance.requesttopay.infrastructure.event;

import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestCreatedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class KafkaPayRequestNotificationAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void notifyPayRequestCreated_shouldPublishCreatedEvent() throws Exception {
        KafkaPayRequestNotificationAdapter adapter = new KafkaPayRequestNotificationAdapter(kafkaTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any(PayRequestCreatedEvent.class))).thenReturn("{\"event\":\"created\"}");

        adapter.notifyPayRequestCreated(sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null));

        verify(kafkaTemplate).send("rtp.pay_requests.v1", "CONS-001", "{\"event\":\"created\"}");
    }

    @Test
    void notifyPayRequestFinalized_shouldPublishRejectedEvent() throws Exception {
        KafkaPayRequestNotificationAdapter adapter = new KafkaPayRequestNotificationAdapter(kafkaTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any(PayRequestRejectedEvent.class))).thenReturn("{\"event\":\"rejected\"}");

        adapter.notifyPayRequestFinalized(sampleRequest(PayRequestStatus.REJECTED, null));

        verify(kafkaTemplate).send("rtp.pay_requests.v1", "CONS-001", "{\"event\":\"rejected\"}");
    }

    @Test
    void notifyPayRequestFinalized_shouldPublishAcceptedEvent() throws Exception {
        KafkaPayRequestNotificationAdapter adapter = new KafkaPayRequestNotificationAdapter(kafkaTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any(PayRequestAcceptedEvent.class))).thenReturn("{\"event\":\"accepted\"}");

        adapter.notifyPayRequestFinalized(sampleRequest(PayRequestStatus.CONSUMED, "PAY-123"));

        verify(kafkaTemplate).send("rtp.pay_requests.v1", "CONS-001", "{\"event\":\"accepted\"}");
    }

    @Test
    void notifyPayRequestFinalized_shouldNotPublishForNonFinalStatus() {
        KafkaPayRequestNotificationAdapter adapter = new KafkaPayRequestNotificationAdapter(kafkaTemplate, objectMapper);

        adapter.notifyPayRequestFinalized(sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null));

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void notifyPayRequestCreated_shouldThrowWhenSerializationFails() throws Exception {
        KafkaPayRequestNotificationAdapter adapter = new KafkaPayRequestNotificationAdapter(kafkaTemplate, objectMapper);
        when(objectMapper.writeValueAsString(any(PayRequestCreatedEvent.class)))
                .thenThrow(new JsonProcessingException("boom") { });

        assertThatThrownBy(() -> adapter.notifyPayRequestCreated(sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Serialization failure")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    private static PayRequest sampleRequest(PayRequestStatus status, String paymentId) {
        return new PayRequest(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                status,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:05:00Z"),
                paymentId
        );
    }
}
