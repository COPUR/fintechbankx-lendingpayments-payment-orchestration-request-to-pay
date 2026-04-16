package com.enterprise.openfinance.requesttopay.infrastructure.persistence.mapper;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity.PayRequestJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PayRequestMapper {

    public PayRequestJpaEntity toEntity(PayRequest domain) {
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
                domain.paymentIdOptional().orElse(null)
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
                entity.getPaymentId()
        );
    }
}
