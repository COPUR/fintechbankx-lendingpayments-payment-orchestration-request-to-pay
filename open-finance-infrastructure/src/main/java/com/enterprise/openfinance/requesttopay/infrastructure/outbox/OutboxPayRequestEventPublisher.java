package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import com.enterprise.openfinance.requesttopay.domain.event.PayRequestDomainEvent;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestEventPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Transactional outbox: writes each event's envelope in the caller's
 * transaction (MANDATORY), so the pay request row and its events commit or
 * roll back together. {@link OutboxRelay} ships them to Kafka afterwards.
 */
@Component
public class OutboxPayRequestEventPublisher implements PayRequestEventPublisher {

    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID = Pattern.compile("[0-9a-f]{16}");

    private final SpringDataOutboxRepository outbox;
    private final PayRequestEventEnvelopeFactory envelopes;

    public OutboxPayRequestEventPublisher(SpringDataOutboxRepository outbox, PayRequestEventEnvelopeFactory envelopes) {
        this.outbox = outbox;
        this.envelopes = envelopes;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(PayRequest payRequest, List<PayRequestDomainEvent> events, String correlationId) {
        String traceparent = currentTraceparent();
        outbox.saveAll(events.stream()
                .map(event -> {
                    OutboxEventJpaEntity row = envelopes.toOutboxRow(payRequest, event, correlationId);
                    row.setTraceparent(traceparent);
                    return row;
                })
                .toList());
    }

    /** W3C traceparent of the current span (Micrometer Tracing puts traceId/spanId in the MDC). */
    static String currentTraceparent() {
        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        if (traceId == null || spanId == null || !TRACE_ID.matcher(traceId).matches()
                || !SPAN_ID.matcher(spanId).matches()) {
            return null;
        }
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
