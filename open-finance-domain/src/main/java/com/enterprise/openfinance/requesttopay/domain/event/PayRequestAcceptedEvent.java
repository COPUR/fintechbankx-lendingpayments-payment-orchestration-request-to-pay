package com.enterprise.openfinance.requesttopay.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The requesting TPP reported acceptance with {@code paymentId}. Neither the debtor's
 * authorisation nor the payment is verified by this service.
 *
 * @param actorClientId the client that made the decision
 * @param reason        optional free text from the decision, or null
 */
public record PayRequestAcceptedEvent(
        UUID eventId,
        String aggregateId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String creditorName,
        String debtorId,
        String actorClientId,
        String reason,
        Instant occurredOn,
        int version
) implements PayRequestDomainEvent {
    public PayRequestAcceptedEvent(
            String aggregateId,
            String paymentId,
            BigDecimal amount,
            String currency,
            String creditorName,
            String debtorId,
            String actorClientId,
            String reason,
            Instant occurredOn
    ) {
        this(UUID.randomUUID(), aggregateId, paymentId, amount, currency, creditorName, debtorId, actorClientId,
                reason, occurredOn, 1);
    }
}
