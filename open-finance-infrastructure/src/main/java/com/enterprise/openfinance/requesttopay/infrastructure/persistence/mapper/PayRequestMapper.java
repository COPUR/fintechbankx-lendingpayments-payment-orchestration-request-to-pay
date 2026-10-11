package com.enterprise.openfinance.requesttopay.infrastructure.persistence.mapper;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity.PayRequestJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Domain record to row and back. A domain version 0 is a new row; for
 * version n the row is expected at version n - 1, and the optimistic lock
 * moves it to n on update.
 */
@Component
public class PayRequestMapper {

    public PayRequestJpaEntity toEntity(PayRequest domain) {
        boolean isNew = domain.version() == 0;
        return new PayRequestJpaEntity(
                domain.consentId(),
                domain.tppId(),
                domain.psuId(),
                domain.creditorName(),
                domain.amount(),
                domain.currency(),
                domain.status().name(),
                domain.requestedAt(),
                domain.updatedAt(),
                domain.paymentIdOptional().orElse(null),
                isNew ? null : domain.version() - 1,
                isNew
        );
    }

    public PayRequest toDomain(PayRequestJpaEntity entity) {
        return new PayRequest(
                entity.getConsentId(),
                entity.getTppId(),
                entity.getPsuId(),
                entity.getCreditorName(),
                entity.getAmount(),
                entity.getCurrency(),
                PayRequestStatus.valueOf(entity.getStatus()),
                entity.getRequestedAt(),
                entity.getUpdatedAt(),
                entity.getPaymentId(),
                entity.getVersion() == null ? 0L : entity.getVersion()
        );
    }
}
