package com.enterprise.openfinance.requesttopay.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayRequestCreatedEvent(
        UUID eventId,
        String aggregateId,
        String creditorName,
        BigDecimal amount,
        String currency,
        String debtorId,
        Instant occurredOn,
        int version
) {
    public PayRequestCreatedEvent(
            String aggregateId,
            String creditorName,
            BigDecimal amount,
            String currency,
            String debtorId,
            Instant occurredOn
    ) {
        this(UUID.randomUUID(), aggregateId, creditorName, amount, currency, debtorId, occurredOn, 1);
    }
}
