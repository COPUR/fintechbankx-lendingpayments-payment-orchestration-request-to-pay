package com.enterprise.openfinance.requesttopay.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PayRequestRejectedEvent(
        UUID eventId,
        String aggregateId,
        Instant occurredOn,
        int version
) {
    public PayRequestRejectedEvent(
            String aggregateId,
            Instant occurredOn
    ) {
        this(UUID.randomUUID(), aggregateId, occurredOn, 1);
    }
}
