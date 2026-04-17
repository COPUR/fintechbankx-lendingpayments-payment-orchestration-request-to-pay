package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;
import com.enterprise.openfinance.requesttopay.infrastructure.persistence.mapper.PayRequestMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Primary
@Component
public class JpaPayRequestRepositoryAdapter implements PayRequestRepositoryPort {

    private final SpringDataPayRequestRepository repository;
    private final PayRequestMapper mapper;

    public JpaPayRequestRepositoryAdapter(SpringDataPayRequestRepository repository, PayRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PayRequest save(PayRequest payRequest) {
        var entity = mapper.toEntity(payRequest);
        var savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PayRequest> findByConsentId(String consentId) {
        return repository.findById(consentId)
                .map(mapper::toDomain);
    }
}
