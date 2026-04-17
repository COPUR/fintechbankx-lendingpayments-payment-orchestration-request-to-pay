package com.enterprise.openfinance.requesttopay.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayRequestAcceptedEvent(
        UUID eventId,
        String aggregateId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String creditorName,
        String debtorId,
        Instant occurredOn,
        int version
) {
    public PayRequestAcceptedEvent(
            String aggregateId,
            String paymentId,
            BigDecimal amount,
            String currency,
            String creditorName,
            String debtorId,
            Instant occurredOn
    ) {
        this(UUID.randomUUID(), aggregateId, paymentId, amount, currency, creditorName, debtorId, occurredOn, 1);
    }
}
