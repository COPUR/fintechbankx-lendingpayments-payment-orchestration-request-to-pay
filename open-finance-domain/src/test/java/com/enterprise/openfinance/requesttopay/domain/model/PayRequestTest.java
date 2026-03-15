package com.enterprise.openfinance.requesttopay.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayRequestTest {

    @Test
    void shouldCreateAwaitingAuthorizationRequest() {
        PayRequest request = new PayRequest(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null
        );

        assertThat(request.status()).isEqualTo(PayRequestStatus.AWAITING_AUTHORISATION);
        assertThat(request.isFinalized()).isFalse();
    }

    @Test
    void shouldTransitionToRejected() {
        PayRequest request = baseRequest();

        PayRequest rejected = request.reject(Instant.parse("2026-02-10T12:00:00Z"));

        assertThat(rejected.status()).isEqualTo(PayRequestStatus.REJECTED);
        assertThat(rejected.isFinalized()).isTrue();
    }

    @Test
    void shouldTransitionToConsumed() {
        PayRequest request = baseRequest();

        PayRequest consumed = request.consume("PAY-001", Instant.parse("2026-02-10T12:10:00Z"));

        assertThat(consumed.status()).isEqualTo(PayRequestStatus.CONSUMED);
        assertThat(consumed.paymentIdOptional()).contains("PAY-001");
    }

    @Test
    void shouldRejectDuplicateFinalize() {
        PayRequest request = baseRequest().consume("PAY-001", Instant.parse("2026-02-10T12:10:00Z"));

        assertThatThrownBy(() -> request.reject(Instant.parse("2026-02-10T12:20:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finalized");
    }

    @Test
    void shouldRejectInvalidFields() {
        assertThatThrownBy(() -> new PayRequest(" ", "TPP", "PSU", "Creditor", new BigDecimal("1.00"), "AED",
                PayRequestStatus.AWAITING_AUTHORISATION, Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consentId");
    }

    private static PayRequest baseRequest() {
        return new PayRequest(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null
        );
    }
}
