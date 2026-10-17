package com.enterprise.openfinance.requesttopay.domain.model;

/**
 * The pay request an idempotency key was first used for, and the fingerprint
 * of the payload it was used with.
 */
public record IdempotencyRecord(String consentId, String requestFingerprint) {

    public IdempotencyRecord {
        if (consentId == null || consentId.isBlank()) {
            throw new IllegalArgumentException("consentId is required");
        }
        if (requestFingerprint == null || requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("requestFingerprint is required");
        }
    }

    public boolean matches(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }
}
