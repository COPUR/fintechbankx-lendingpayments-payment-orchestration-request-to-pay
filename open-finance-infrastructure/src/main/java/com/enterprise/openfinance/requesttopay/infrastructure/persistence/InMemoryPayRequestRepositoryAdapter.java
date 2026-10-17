package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double for application and web tests. Not a Spring bean: the service
 * always runs against PostgreSQL (JpaPayRequestRepositoryAdapter).
 */
public class InMemoryPayRequestRepositoryAdapter implements PayRequestRepositoryPort {

    private final ConcurrentHashMap<String, PayRequest> store = new ConcurrentHashMap<>();

    @Override
    public PayRequest save(PayRequest request) {
        PayRequest stored = new PayRequest(request.consentId(), request.tppId(), request.psuId(),
                request.creditorName(), request.amount(), request.currency(), request.status(),
                request.requestedAt(), request.updatedAt(), request.paymentId(), request.version());
        store.put(request.consentId(), stored);
        return request;
    }

    @Override
    public Optional<PayRequest> findByConsentId(String consentId) {
        return Optional.ofNullable(store.get(consentId));
    }
}
