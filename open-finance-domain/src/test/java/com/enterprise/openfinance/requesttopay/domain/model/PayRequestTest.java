package com.enterprise.openfinance.requesttopay.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayRequestTest {

    @Test
    void shouldCreateValidPayRequest() {
        PayRequest request = new PayRequest(
                "consent-123",
                "tpp-456",
                "psu-789",
                "Acme Corp",
                new BigDecimal("100.00"),
                "usd",
                PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.now(),
                Instant.now(),
                null
        );

        assertThat(request.consentId()).isEqualTo("consent-123");
        assertThat(request.currency()).isEqualTo("USD");
        assertThat(request.isFinalized()).isFalse();
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> new PayRequest(
                "consent-123", "tpp-456", "psu-789", "Acme Corp",
                new BigDecimal("-10.00"), "USD", PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.now(), Instant.now(), null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
    }

    @Test
    void shouldRejectBlankFields() {
        assertThatThrownBy(() -> new PayRequest(
                "  ", "tpp-456", "psu-789", "Acme Corp",
                new BigDecimal("10.00"), "USD", PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.now(), Instant.now(), null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("consentId");
    }

    @Test
    void shouldAllowRejectionIfNotFinalized() {
        PayRequest request = new PayRequest(
                "consent-123", "tpp-456", "psu-789", "Acme Corp",
                new BigDecimal("100.00"), "USD", PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.now(), Instant.now(), null
        );

        Instant rejectionTime = Instant.now();
        PayRequest rejected = request.reject(rejectionTime);

        assertThat(rejected.status()).isEqualTo(PayRequestStatus.REJECTED);
        assertThat(rejected.updatedAt()).isEqualTo(rejectionTime);
        assertThat(rejected.isFinalized()).isTrue();
    }

    @Test
    void shouldAllowConsumptionIfNotFinalized() {
        PayRequest request = new PayRequest(
                "consent-123", "tpp-456", "psu-789", "Acme Corp",
                new BigDecimal("100.00"), "USD", PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.now(), Instant.now(), null
        );

        Instant consumptionTime = Instant.now();
        PayRequest consumed = request.consume("payment-999", consumptionTime);

        assertThat(consumed.status()).isEqualTo(PayRequestStatus.CONSUMED);
        assertThat(consumed.updatedAt()).isEqualTo(consumptionTime);
        assertThat(consumed.paymentId()).isEqualTo("payment-999");
        assertThat(consumed.isFinalized()).isTrue();
    }

    @Test
    void shouldPreventRejectionIfAlreadyFinalized() {
        PayRequest request = new PayRequest(
                "consent-123", "tpp-456", "psu-789", "Acme Corp",
                new BigDecimal("100.00"), "USD", PayRequestStatus.REJECTED,
                Instant.now(), Instant.now(), null
        );

        assertThatThrownBy(() -> request.reject(Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already finalized");
    }

    @Test
    void shouldCheckBelongsTo() {
        PayRequest request = new PayRequest(
                "consent-123", "tpp-456", "psu-789", "Acme Corp",
                new BigDecimal("100.00"), "USD", PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.now(), Instant.now(), null
        );

        assertThat(request.belongsTo("tpp-456")).isTrue();
        assertThat(request.belongsTo("other-tpp")).isFalse();
    }
}