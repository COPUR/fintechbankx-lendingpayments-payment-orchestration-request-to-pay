package com.enterprise.openfinance.requesttopay.domain.model;

import java.time.Duration;

/**
 * @param cacheTtl        how long a status read may be served from the cache
 * @param idempotencyTtl  how long an x-idempotency-key is remembered
 */
public record PayRequestSettings(Duration cacheTtl, Duration idempotencyTtl) {

    public static final Duration DEFAULT_IDEMPOTENCY_TTL = Duration.ofHours(24);

    public PayRequestSettings {
        requirePositive(cacheTtl, "cacheTtl");
        requirePositive(idempotencyTtl, "idempotencyTtl");
    }

    public PayRequestSettings(Duration cacheTtl) {
        this(cacheTtl, DEFAULT_IDEMPOTENCY_TTL);
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
