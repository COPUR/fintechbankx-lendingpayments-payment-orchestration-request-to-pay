package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPayRequestRepositoryAdapter implements PayRequestRepositoryPort {

    private final ConcurrentHashMap<String, PayRequest> store = new ConcurrentHashMap<>();

    @Override
    public PayRequest save(PayRequest request) {
        store.put(request.consentId(), request);
        return request;
    }

    @Override
    public Optional<PayRequest> findById(String consentId) {
        return Optional.ofNullable(store.get(consentId));
    }
}
