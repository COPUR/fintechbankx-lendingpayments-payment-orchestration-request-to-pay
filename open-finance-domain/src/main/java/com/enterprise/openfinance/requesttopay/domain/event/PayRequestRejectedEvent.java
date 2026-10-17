package com.enterprise.openfinance.requesttopay.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The requesting TPP reported that the debtor declined.
 *
 * @param actorClientId the client that made the decision
 * @param reason        optional free text from the decision, or null
 */
public record PayRequestRejectedEvent(
        UUID eventId,
        String aggregateId,
        String actorClientId,
        String reason,
        Instant occurredOn,
        int version
) implements PayRequestDomainEvent {
    public PayRequestRejectedEvent(
            String aggregateId,
            String actorClientId,
            String reason,
            Instant occurredOn
    ) {
        this(UUID.randomUUID(), aggregateId, actorClientId, reason, occurredOn, 1);
    }
}
