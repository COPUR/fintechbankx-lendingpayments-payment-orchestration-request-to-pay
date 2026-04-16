package com.enterprise.openfinance.requesttopay.infrastructure.persistence;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.infrastructure.persistence.entity.PayRequestJpaEntity;
import com.enterprise.openfinance.requesttopay.infrastructure.persistence.mapper.PayRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class JpaPayRequestRepositoryAdapterTest {

    @Mock
    private SpringDataPayRequestRepository repository;

    @Mock
    private PayRequestMapper mapper;

    private JpaPayRequestRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaPayRequestRepositoryAdapter(repository, mapper);
    }

    @Test
    void save_shouldPersistUsingRepositoryAndMapper() {
        PayRequest domain = sampleRequest();
        PayRequestJpaEntity entity = sampleEntity();
        PayRequestJpaEntity savedEntity = sampleEntity();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDomain(savedEntity)).thenReturn(domain);

        PayRequest saved = adapter.save(domain);

        assertThat(saved).isEqualTo(domain);
        verify(mapper).toEntity(domain);
        verify(repository).save(entity);
        verify(mapper).toDomain(savedEntity);
    }

    @Test
    void findByConsentId_shouldReturnMappedEntityWhenFound() {
        PayRequest domain = sampleRequest();
        PayRequestJpaEntity entity = sampleEntity();
        when(repository.findById("CONS-001")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<PayRequest> found = adapter.findByConsentId("CONS-001");

        assertThat(found).contains(domain);
        verify(repository).findById("CONS-001");
        verify(mapper).toDomain(entity);
    }

    @Test
    void findByConsentId_shouldReturnEmptyWhenMissing() {
        when(repository.findById("CONS-001")).thenReturn(Optional.empty());

        Optional<PayRequest> found = adapter.findByConsentId("CONS-001");

        assertThat(found).isEmpty();
        verify(repository).findById("CONS-001");
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
                Instant.parse("2026-02-10T10:05:00Z"),
                null
        );
    }

    private static PayRequestJpaEntity sampleEntity() {
        return new PayRequestJpaEntity(
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
    }
}
