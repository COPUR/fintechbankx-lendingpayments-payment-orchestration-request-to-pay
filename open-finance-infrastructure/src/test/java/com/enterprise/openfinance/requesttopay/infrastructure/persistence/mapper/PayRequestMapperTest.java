package com.enterprise.openfinance.requesttopay.infrastructure.persistence.mapper;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity.PayRequestJpaEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PayRequestMapperTest {

    private final PayRequestMapper mapper = new PayRequestMapper();

    @Test
    void toEntity_shouldMapAllDomainFields() {
        PayRequest domain = sampleRequest(PayRequestStatus.CONSUMED, "PAY-123");

        PayRequestJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity.getConsentId()).isEqualTo(domain.consentId());
        assertThat(entity.getTppId()).isEqualTo(domain.tppId());
        assertThat(entity.getPsuId()).isEqualTo(domain.psuId());
        assertThat(entity.getCreditorName()).isEqualTo(domain.creditorName());
        assertThat(entity.getAmount()).isEqualByComparingTo(domain.amount());
        assertThat(entity.getCurrency()).isEqualTo(domain.currency());
        assertThat(entity.getStatus()).isEqualTo(domain.status().name());
        assertThat(entity.getRequestedAt()).isEqualTo(domain.requestedAt());
        assertThat(entity.getUpdatedAt()).isEqualTo(domain.updatedAt());
        assertThat(entity.getPaymentId()).isEqualTo("PAY-123");
    }

    @Test
    void toDomain_shouldMapAllEntityFields() {
        PayRequestJpaEntity entity = new PayRequestJpaEntity(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                "AWAITING_AUTHORISATION",
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:05:00Z"),
                null
        );

        PayRequest domain = mapper.toDomain(entity);

        assertThat(domain.consentId()).isEqualTo("CONS-001");
        assertThat(domain.tppId()).isEqualTo("TPP-001");
        assertThat(domain.psuId()).isEqualTo("PSU-001");
        assertThat(domain.creditorName()).isEqualTo("Utilities Co");
        assertThat(domain.amount()).isEqualByComparingTo("500.00");
        assertThat(domain.currency()).isEqualTo("AED");
        assertThat(domain.status()).isEqualTo(PayRequestStatus.AWAITING_AUTHORISATION);
        assertThat(domain.requestedAt()).isEqualTo(Instant.parse("2026-02-10T10:00:00Z"));
        assertThat(domain.updatedAt()).isEqualTo(Instant.parse("2026-02-10T10:05:00Z"));
        assertThat(domain.paymentId()).isNull();
    }

    private static PayRequest sampleRequest(PayRequestStatus status, String paymentId) {
        return new PayRequest(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                status,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:05:00Z"),
                paymentId
        );
    }
}
