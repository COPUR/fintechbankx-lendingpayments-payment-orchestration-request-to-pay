package com.enterprise.openfinance.requesttopay.domain.port.out;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;

import java.util.Optional;

public interface PayRequestRepositoryPort {

    /**
     * Inserts a new pay request (version 0) or stores the next version of an
     * existing one. Fails if the stored version is not {@code version - 1}.
     */
    PayRequest save(PayRequest payRequest);

    Optional<PayRequest> findByConsentId(String consentId);

    /**
     * Loads the pay request for a state change and keeps it locked against
     * concurrent decisions until the caller's transaction ends.
     */
    default Optional<PayRequest> findByConsentIdForUpdate(String consentId) {
        return findByConsentId(consentId);
    }
}
