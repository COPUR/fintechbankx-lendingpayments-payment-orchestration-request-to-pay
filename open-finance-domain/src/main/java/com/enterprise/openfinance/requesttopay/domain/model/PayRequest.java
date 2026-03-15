package com.enterprise.openfinance.requesttopay.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record PayRequest(
        String consentId,
        String tppId,
        String psuId,
        String creditorName,
        BigDecimal amount,
        String currency,
        PayRequestStatus status,
        Instant requestedAt,
        Instant updatedAt,
        String paymentId
) {

    public PayRequest {
        consentId = requireNotBlank(consentId, "consentId");
        tppId = requireNotBlank(tppId, "tppId");
        psuId = requireNotBlank(psuId, "psuId");
        creditorName = requireNotBlank(creditorName, "creditorName");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireNotBlank(currency, "currency").toUpperCase();
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }
    }

    public boolean belongsTo(String tppIdValue) {
        return tppId.equals(tppIdValue);
    }

    public boolean isFinalized() {
        return status.isFinal();
    }

    public PayRequest reject(Instant now) {
        ensureNotFinalized();
        return new PayRequest(consentId, tppId, psuId, creditorName, amount, currency,
                PayRequestStatus.REJECTED, requestedAt, now, paymentId);
    }

    public PayRequest consume(String paymentIdValue, Instant now) {
        ensureNotFinalized();
        String resolvedPaymentId = requireNotBlank(paymentIdValue, "paymentId");
        return new PayRequest(consentId, tppId, psuId, creditorName, amount, currency,
                PayRequestStatus.CONSUMED, requestedAt, now, resolvedPaymentId);
    }

    private void ensureNotFinalized() {
        if (isFinalized()) {
            throw new IllegalStateException("Pay request already finalized");
        }
    }

    public Optional<String> paymentIdOptional() {
        return Optional.ofNullable(paymentId);
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
