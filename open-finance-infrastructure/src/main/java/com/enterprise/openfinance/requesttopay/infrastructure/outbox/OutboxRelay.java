package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Relays committed outbox rows to Kafka in insertion order.
 *
 * <p>Single relay: one replica relays at a time, through a session-level
 * Postgres advisory lock on a connection of its own ({@link RelayLock}). No
 * database transaction is open while a send is in flight: the batch is read
 * in one short transaction, each send runs outside any transaction, and each
 * outcome is written in its own short transaction. A run stops starting new
 * sends once {@code runBudget} has elapsed, so a run (budget + producer
 * max.block.ms + send timeout) ends inside the shutdown phase.
 *
 * <p>Per-aggregate order: a failed row stops the batch, so nothing behind it
 * is sent before it. A PARKED row keeps the later rows of its aggregate
 * (record key) pending until an operator replays it, while other aggregates
 * keep flowing ({@link SpringDataOutboxRepository#findPendingBatch}).
 *
 * <p>A failed send is handled by what failed (ADR-021 decision 4):
 * <ul>
 *   <li>payload error that can never succeed for that row (RecordTooLarge,
 *   Serialization, InvalidTopic): the row is PARKED (error, parked_at and
 *   park_reason recorded; outbox.parked.events alerts), its aggregate's later
 *   rows stay pending, and the batch continues with other aggregates;</li>
 *   <li>every other error (broker timeouts and other retriable errors, SASL/IAM
 *   authentication, topic authorisation, producer construction, unclassified):
 *   the batch stops without marking the row or anything after it. Such a row is
 *   never parked automatically; only an operator parks it, with a recorded
 *   reason (runbook).</li>
 * </ul>
 * Every failure increments outbox.send.failures, tagged only by the
 * exception class (no identifiers in tags or messages).
 * After a run that stopped on a failure the relay backs off (1 s doubling to
 * 60 s); outbox.relay.consecutive.failed.runs and outbox.oldest.pending.age.seconds
 * alert. A clean run resets the backoff.
 * Consumers de-duplicate on eventId (at-least-once delivery).
 */
public class OutboxRelay {

    public static final long RELAY_LOCK_KEY = 0x7274705F6F7574L; // "rtp_out"
    static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    static final String PAYLOAD_PARK_REASON = "payload error (relay)";

    /** How a failed send is handled. */
    enum Failure { PAYLOAD, STOP }

    /**
     * @param runBudget no new send starts after this much time in one run
     */
    public record Settings(int batchSize, Duration sendTimeout, Duration runBudget, Duration retention) {
        public Settings {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("requesttopay.outbox.relay.batch-size must be positive");
            }
            requirePositive(sendTimeout, "send-timeout");
            requirePositive(runBudget, "run-budget");
            requirePositive(retention, "retention");
        }

        private static void requirePositive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("requesttopay.outbox.relay." + name + " must be positive");
            }
        }
    }

    private final SpringDataOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionOperations transactions;
    private final RelayLock lock;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Settings settings;
    private volatile int consecutiveFailedRuns;
    private volatile Instant nextRunAt = Instant.MIN;

    public OutboxRelay(SpringDataOutboxRepository outbox, KafkaTemplate<String, String> kafka,
                       TransactionOperations transactions, RelayLock lock, Clock clock, MeterRegistry meters,
                       Settings settings) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transactions = transactions;
        this.lock = lock;
        this.clock = clock;
        this.meters = meters;
        this.settings = settings;
    }

    /**
     * @return number of events published in this run
     */
    public int relayOnce() {
        if (clock.instant().isBefore(nextRunAt)) {
            return 0;
        }
        Optional<RelayLock.Held> held = lock.tryAcquire();
        if (held.isEmpty()) {
            return 0;
        }
        try (RelayLock.Held ignored = held.get()) {
            return relayBatch();
        }
    }

    /** Runs in a row that stopped on a failure; 0 after a clean run. Alert when it stays above 0. */
    public int consecutiveFailedRuns() {
        return consecutiveFailedRuns;
    }

    private void runFailed() {
        consecutiveFailedRuns++;
        long factor = 1L << Math.min(consecutiveFailedRuns - 1, 6);
        Duration backoff = FIRST_BACKOFF.multipliedBy(factor);
        nextRunAt = clock.instant().plus(backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff);
    }

    private void runSucceeded() {
        consecutiveFailedRuns = 0;
        nextRunAt = Instant.MIN;
    }

    private int relayBatch() {
        List<OutboxEventJpaEntity> batch = transactions.execute(status -> outbox.findPendingBatch(settings.batchSize()));
        if (batch == null || batch.isEmpty()) {
            runSucceeded();
            return 0;
        }
        Instant started = clock.instant();
        Set<String> blockedAggregates = new HashSet<>();
        int sent = 0;
        for (OutboxEventJpaEntity row : batch) {
            if (blockedAggregates.contains(row.getAggregateId())) {
                continue;
            }
            if (Duration.between(started, clock.instant()).compareTo(settings.runBudget()) >= 0) {
                break;
            }
            try {
                kafka.send(toRecord(row)).get(settings.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                Instant now = clock.instant();
                update(row.getEventId(), r -> r.markPublished(now));
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Outbox relay interrupted (shutdown); event {} stays pending", row.getEventId());
                return sent;
            } catch (Exception e) {
                String exception = rootCause(e).getClass().getSimpleName();
                meters.counter("outbox.send.failures", "exception", exception).increment();
                if (classify(e) == Failure.PAYLOAD) {
                    // later rows of this aggregate wait behind the parked row
                    blockedAggregates.add(row.getAggregateId());
                    park(row, describe(e));
                    continue;
                }
                log.error("Outbox relay stopped at event {} for {} ({}); nothing marked, retried after backoff",
                        row.getEventId(), row.getTopic(), exception, e);
                runFailed();
                return sent;
            }
        }
        runSucceeded();
        return sent;
    }

    private void park(OutboxEventJpaEntity row, String error) {
        Instant now = clock.instant();
        update(row.getEventId(), r -> r.park(error, PAYLOAD_PARK_REASON, now));
        log.error("Outbox relay parked event {} for {}: {}; its aggregate's later events wait; replay it by hand",
                row.getEventId(), row.getTopic(), error);
    }

    private void update(UUID eventId, Consumer<OutboxEventJpaEntity> change) {
        transactions.executeWithoutResult(status -> outbox.findById(eventId).ifPresent(change));
    }

    public int purgePublished() {
        Integer deleted = transactions.execute(status ->
                outbox.deletePublishedBefore(clock.instant().minus(settings.retention())));
        return deleted == null ? 0 : deleted;
    }

    static Failure classify(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordTooLargeException || cause instanceof SerializationException
                    || cause instanceof InvalidTopicException) {
                return Failure.PAYLOAD;
            }
        }
        return Failure.STOP;
    }

    /** The failure without the future and KafkaTemplate wrappers. */
    static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException
                || cause instanceof KafkaProducerException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** last_error of a parked row: the Kafka client's message (topic and size, no pay request data). */
    static String describe(Throwable failure) {
        Throwable cause = rootCause(failure);
        return cause.getMessage() == null
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    static ProducerRecord<String, String> toRecord(OutboxEventJpaEntity row) {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.getTopic(), row.getAggregateId(), row.getPayload());
        record.headers().add("eventType", row.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", row.getEventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlationId", row.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("x-fapi-interaction-id", row.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        if (row.getTraceparent() != null) {
            record.headers().add("traceparent", row.getTraceparent().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
