package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class InMemoryPayRequestRepositoryAdapterTest {

    @Test
    void shouldPersistAndRetrieve() {
        InMemoryPayRequestRepositoryAdapter adapter = new InMemoryPayRequestRepositoryAdapter();
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

        adapter.save(request);

        assertThat(adapter.findById("CONS-001")).contains(request);
    }
}
