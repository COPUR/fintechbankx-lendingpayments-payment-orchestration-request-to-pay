package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.infrastructure.idempotency.JdbcPayRequestIdempotencyAdapter;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxEventJpaEntity;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxRelay;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.PayRequestEventEnvelopeFactory;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.SpringDataOutboxRepository;
import com.enterprise.openfinance.requesttopay.infrastructure.security.JdbcDPoPNonceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Outbox wiring, its metrics, and the housekeeping of the tables that only
 * hold short-lived rows (idempotency keys, DPoP jti).
 */
@Configuration
@EnableScheduling
public class OutboxConfiguration {

    @Bean
    PayRequestEventEnvelopeFactory payRequestEventEnvelopeFactory(ObjectMapper objectMapper) {
        return new PayRequestEventEnvelopeFactory(objectMapper);
    }

    /** Events written but not yet on Kafka. Alert on growth: relay or brokers are down. */
    @Bean
    Gauge outboxPendingGauge(MeterRegistry registry, SpringDataOutboxRepository outbox) {
        return Gauge.builder("outbox_pending_events", outbox,
                        o -> o.countByStatus(OutboxEventJpaEntity.Status.PENDING))
                .description("Pay request events in the outbox not yet published to Kafka")
                .register(registry);
    }

    /** Events that failed max-attempts times and need an operator (dead-letter state). */
    @Bean
    Gauge outboxParkedGauge(MeterRegistry registry, SpringDataOutboxRepository outbox) {
        return Gauge.builder("outbox_parked_events", outbox,
                        o -> o.countByStatus(OutboxEventJpaEntity.Status.PARKED))
                .description("Pay request events parked after the maximum number of publish attempts")
                .register(registry);
    }

    @Bean
    Gauge outboxOldestPendingAgeGauge(MeterRegistry registry, SpringDataOutboxRepository outbox, Clock clock) {
        return Gauge.builder("outbox_oldest_pending_age_seconds", outbox,
                        o -> o.oldestPendingOccurredAt()
                                .map(at -> (double) Duration.between(at, clock.instant()).toSeconds())
                                .orElse(0.0))
                .description("Age of the oldest unpublished pay request event")
                .register(registry);
    }

    @Bean
    Housekeeping requestToPayHousekeeping(JdbcPayRequestIdempotencyAdapter idempotency,
                                          JdbcDPoPNonceRepository dpopJti, Clock clock) {
        return new Housekeeping(idempotency, dpopJti, clock);
    }

    /**
     * The relay runs in every replica when enabled; the advisory lock lets
     * only one of them publish at a time. Off by default until the
     * evt.pay.rtp.*.v1 topics exist (OUTBOX_RELAY_ENABLED=true turns it on).
     */
    @Configuration
    @ConditionalOnProperty(name = "requesttopay.outbox.relay.enabled", havingValue = "true")
    static class RelayConfiguration {

        @Bean
        OutboxRelay outboxRelay(SpringDataOutboxRepository outbox,
                                KafkaTemplate<String, String> kafka,
                                PlatformTransactionManager transactionManager,
                                Clock clock,
                                @Value("${requesttopay.outbox.relay.batch-size:100}") int batchSize,
                                @Value("${requesttopay.outbox.relay.max-attempts:10}") int maxAttempts,
                                @Value("${requesttopay.outbox.relay.send-timeout:PT10S}") Duration sendTimeout,
                                @Value("${requesttopay.outbox.retention:P7D}") Duration retention) {
            return new OutboxRelay(outbox, kafka, new TransactionTemplate(transactionManager), clock, batchSize,
                    maxAttempts, sendTimeout, retention);
        }

        @Bean
        RelaySchedule relaySchedule(OutboxRelay relay) {
            return new RelaySchedule(relay);
        }
    }

    static class RelaySchedule {
        private final OutboxRelay relay;

        RelaySchedule(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${requesttopay.outbox.relay.interval:PT1S}")
        void relay() {
            relay.relayOnce();
        }

        @Scheduled(cron = "${requesttopay.outbox.purge-cron:0 15 3 * * *}")
        void purge() {
            relay.purgePublished();
        }
    }

    static class Housekeeping {
        private final JdbcPayRequestIdempotencyAdapter idempotency;
        private final JdbcDPoPNonceRepository dpopJti;
        private final Clock clock;

        Housekeeping(JdbcPayRequestIdempotencyAdapter idempotency, JdbcDPoPNonceRepository dpopJti, Clock clock) {
            this.idempotency = idempotency;
            this.dpopJti = dpopJti;
            this.clock = clock;
        }

        @Scheduled(fixedDelayString = "${requesttopay.housekeeping.interval:PT10M}")
        void purgeExpired() {
            idempotency.purgeExpired(clock.instant());
            dpopJti.purgeExpired(clock.instant());
        }
    }
}
