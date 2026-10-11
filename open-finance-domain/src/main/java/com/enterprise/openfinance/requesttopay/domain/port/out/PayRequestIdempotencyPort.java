package com.enterprise.openfinance.requesttopay.domain.port.out;

import com.enterprise.openfinance.requesttopay.domain.model.IdempotencyRecord;

import java.time.Instant;
import java.util.Optional;

/**
 * Remembers x-idempotency-key per TPP. Must take part in the caller's
 * transaction, so a reservation disappears if creating the pay request fails.
 */
public interface PayRequestIdempotencyPort {

    /**
     * Reserves the key for {@code consentId} if the TPP has not used it (or it expired).
     *
     * @return empty when the key was reserved now; otherwise the earlier use of the key
     */
    Optional<IdempotencyRecord> reserve(String tppId, String idempotencyKey, String requestFingerprint,
                                        String consentId, Instant now, Instant expiresAt);
}
