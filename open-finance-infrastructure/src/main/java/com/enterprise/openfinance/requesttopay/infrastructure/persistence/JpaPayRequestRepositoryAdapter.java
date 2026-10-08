package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;
import com.enterprise.openfinance.requesttopay.infrastructure.persistence.mapper.PayRequestMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * PostgreSQL adapter (schema sc_pay_request_to_pay). Inserts version 0 and
 * updates later versions under the optimistic lock; decisions additionally
 * take a row lock through {@link #findByConsentIdForUpdate(String)}.
 */
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
        var savedEntity = repository.saveAndFlush(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PayRequest> findByConsentId(String consentId) {
        return repository.findById(consentId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PayRequest> findByConsentIdForUpdate(String consentId) {
        return repository.findByIdForUpdate(consentId)
                .map(mapper::toDomain);
    }
}
