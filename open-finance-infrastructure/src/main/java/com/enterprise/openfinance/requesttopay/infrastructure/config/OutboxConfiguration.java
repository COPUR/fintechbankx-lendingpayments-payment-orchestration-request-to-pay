package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.infrastructure.idempotency.JdbcPayRequestIdempotencyAdapter;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxEventJpaEntity;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxRelay;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.PayRequestEventEnvelopeFactory;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.PostgresSessionRelayLock;
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

import javax.sql.DataSource;
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

    /*
     * Meter names are dotted (Micrometer); Prometheus shows them as outbox_pending_events,
     * outbox_parked_rows and outbox_oldest_pending_age_seconds. The relay adds the counters
     * outbox_parked_events_total and outbox_send_failures_total, tagged by exception class only.
     * No meter carries an identifier (consent, PSU, TPP) as a tag. The parked rows gauge is not
     * called outbox.parked.events: Prometheus would drop the counter of that name.
     */

    /** Events written but not yet on Kafka. Alert on growth: relay or brokers are down. */
    @Bean
    Gauge outboxPendingGauge(MeterRegistry registry, SpringDataOutboxRepository outbox) {
        return Gauge.builder("outbox.pending.events", outbox,
                        o -> o.countByStatus(OutboxEventJpaEntity.Status.PENDING))
                .description("Pay request events in the outbox not yet published to Kafka")
                .register(registry);
    }

    /**
     * Parked events (payload error, or parked by an operator). Each one also
     * holds back its aggregate's later events; alert on any value above 0.
     */
    @Bean
    Gauge outboxParkedGauge(MeterRegistry registry, SpringDataOutboxRepository outbox) {
        return Gauge.builder("outbox.parked.rows", outbox,
                        o -> o.countByStatus(OutboxEventJpaEntity.Status.PARKED))
                .description("Pay request events parked by the outbox relay; their aggregates' later events wait")
                .register(registry);
    }

    @Bean
    Gauge outboxOldestPendingAgeGauge(MeterRegistry registry, SpringDataOutboxRepository outbox, Clock clock) {
        return Gauge.builder("outbox.oldest.pending.age.seconds", outbox,
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
     * The relay runs in every replica when enabled; a session-level advisory lock on a
     * connection of its own lets only one of them publish at a time. Off by default until
     * the aggregate topic evt.pay.rtp.v1 exists and the payments owner has decided who may accept
     * a pay request (OUTBOX_RELAY_ENABLED=true turns it on; runbook).
     */
    @Configuration
    @ConditionalOnProperty(name = "requesttopay.outbox.relay.enabled", havingValue = "true")
    static class RelayConfiguration {

        @Bean
        OutboxRelay outboxRelay(SpringDataOutboxRepository outbox,
                                KafkaTemplate<String, String> kafka,
                                PlatformTransactionManager transactionManager,
                                DataSource dataSource,
                                Clock clock,
                                MeterRegistry meters,
                                @Value("${requesttopay.outbox.relay.batch-size:100}") int batchSize,
                                @Value("${requesttopay.outbox.relay.send-timeout:PT13S}") Duration sendTimeout,
                                @Value("${requesttopay.outbox.relay.run-budget:PT10S}") Duration runBudget,
                                @Value("${requesttopay.outbox.retention:P7D}") Duration retention) {
            return new OutboxRelay(outbox, kafka, new TransactionTemplate(transactionManager),
                    new PostgresSessionRelayLock(dataSource, OutboxRelay.RELAY_LOCK_KEY), clock, meters,
                    new OutboxRelay.Settings(batchSize, sendTimeout, runBudget, retention));
        }

        /** Relay runs in a row that stopped on a failure (auth, broker); alert when above 0 for 5 min. */
        @Bean
        Gauge outboxRelayFailedRunsGauge(MeterRegistry registry, OutboxRelay relay) {
            return Gauge.builder("outbox.relay.consecutive.failed.runs", relay, OutboxRelay::consecutiveFailedRuns)
                    .description("Consecutive outbox relay runs that stopped on a send failure")
                    .register(registry);
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
