package com.enterprise.openfinance.requesttopay.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayRequestResultTest {

    @Test
    void shouldExposeStatusAndCacheFlag() {
        PayRequest request = sampleRequest();

        PayRequestResult result = new PayRequestResult(request, false);

        assertThat(result.status()).isEqualTo(PayRequestStatus.AWAITING_AUTHORISATION);
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.withCacheHit(true).cacheHit()).isTrue();
    }

    @Test
    void shouldRejectNullRequest() {
        assertThatThrownBy(() -> new PayRequestResult(null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request is required");
    }

    private static PayRequest sampleRequest() {
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
