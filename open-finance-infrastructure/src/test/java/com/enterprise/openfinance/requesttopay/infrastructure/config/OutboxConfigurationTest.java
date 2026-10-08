package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.infrastructure.idempotency.JdbcPayRequestIdempotencyAdapter;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxEventJpaEntity;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxRelay;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.SpringDataOutboxRepository;
import com.enterprise.openfinance.requesttopay.infrastructure.security.JdbcDPoPNonceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-02-10T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SpringDataOutboxRepository outbox = mock(SpringDataOutboxRepository.class);
    private final OutboxConfiguration configuration = new OutboxConfiguration();

    @Test
    void exposesPendingParkedAndOldestPendingAgeMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(outbox.countByStatus(OutboxEventJpaEntity.Status.PENDING)).thenReturn(7L);
        when(outbox.countByStatus(OutboxEventJpaEntity.Status.PARKED)).thenReturn(2L);
        when(outbox.oldestPendingOccurredAt()).thenReturn(Optional.of(NOW.minusSeconds(90)));

        configuration.outboxPendingGauge(registry, outbox);
        configuration.outboxParkedGauge(registry, outbox);
        configuration.outboxOldestPendingAgeGauge(registry, outbox, clock);

        assertThat(registry.get("outbox_pending_events").gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("outbox_parked_events").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("outbox_oldest_pending_age_seconds").gauge().value()).isEqualTo(90.0);
    }

    @Test
    void oldestPendingAgeIsZeroWhenNothingIsPending() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(outbox.oldestPendingOccurredAt()).thenReturn(Optional.empty());

        configuration.outboxOldestPendingAgeGauge(registry, outbox, clock);

        assertThat(registry.get("outbox_oldest_pending_age_seconds").gauge().value()).isZero();
    }

    @Test
    void envelopeFactoryUsesTheApplicationObjectMapper() {
        assertThat(configuration.payRequestEventEnvelopeFactory(new ObjectMapper())).isNotNull();
    }

    @Test
    void housekeepingPurgesExpiredIdempotencyKeysAndDpopJti() {
        JdbcPayRequestIdempotencyAdapter idempotency = mock(JdbcPayRequestIdempotencyAdapter.class);
        JdbcDPoPNonceRepository jti = mock(JdbcDPoPNonceRepository.class);

        configuration.requestToPayHousekeeping(idempotency, jti, clock).purgeExpired();

        verify(idempotency).purgeExpired(NOW);
        verify(jti).purgeExpired(NOW);
    }

    @Test
    @SuppressWarnings("unchecked")
    void relayScheduleRelaysAndPurges() {
        OutboxConfiguration.RelayConfiguration relayConfiguration = new OutboxConfiguration.RelayConfiguration();
        OutboxRelay relay = relayConfiguration.outboxRelay(outbox, mock(KafkaTemplate.class),
                mock(PlatformTransactionManager.class), clock, 50, 10, Duration.ofSeconds(5), Duration.ofDays(7));
        OutboxRelay mockedRelay = mock(OutboxRelay.class);

        OutboxConfiguration.RelaySchedule schedule = relayConfiguration.relaySchedule(mockedRelay);
        schedule.relay();
        schedule.purge();

        assertThat(relay).isNotNull();
        verify(mockedRelay).relayOnce();
        verify(mockedRelay).purgePublished();
    }
}
