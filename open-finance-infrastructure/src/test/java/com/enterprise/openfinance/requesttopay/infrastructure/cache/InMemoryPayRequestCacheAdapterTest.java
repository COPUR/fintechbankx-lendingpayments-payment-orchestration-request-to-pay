package com.enterprise.openfinance.requesttopay.infrastructure.cache;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class InMemoryPayRequestCacheAdapterTest {

    @Test
    void shouldEvictExpiredEntries() {
        InMemoryPayRequestCacheAdapter adapter = new InMemoryPayRequestCacheAdapter();
        PayRequestResult result = new PayRequestResult(sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION), false);

        adapter.putStatus("key", result, Instant.parse("2026-02-10T10:05:00Z"));

        assertThat(adapter.getStatus("key", Instant.parse("2026-02-10T10:00:00Z"))).isPresent();
        assertThat(adapter.getStatus("key", Instant.parse("2026-02-10T10:06:00Z"))).isEmpty();
    }

    private static PayRequest sampleRequest(PayRequestStatus status) {
        return new PayRequest(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                status,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null
        );
    }
}
