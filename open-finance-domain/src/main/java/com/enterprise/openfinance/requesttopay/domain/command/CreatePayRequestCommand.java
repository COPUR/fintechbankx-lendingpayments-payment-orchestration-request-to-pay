package com.enterprise.openfinance.requesttopay.domain.command;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePayRequestCommand(
        String tppId,
        String psuId,
        String creditorName,
        BigDecimal amount,
        String currency,
        Instant requestedAt,
        String interactionId
) {

    public CreatePayRequestCommand {
        tppId = requireNotBlank(tppId, "tppId");
        psuId = requireNotBlank(psuId, "psuId");
        creditorName = requireNotBlank(creditorName, "creditorName");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireNotBlank(currency, "currency").toUpperCase();
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt is required");
        }
        interactionId = requireNotBlank(interactionId, "interactionId");
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
