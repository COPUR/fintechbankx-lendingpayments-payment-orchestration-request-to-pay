package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-02-10T12:00:00Z");

    private final SpringDataOutboxRepository outbox = mock(SpringDataOutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final FakeLock lock = new FakeLock();
    private final MutableClock clock = new MutableClock(NOW);
    private final Map<UUID, OutboxEventJpaEntity> rows = new HashMap<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = relay(Duration.ofSeconds(1), Duration.ofSeconds(10));
        when(outbox.findById(any())).thenAnswer(call -> Optional.ofNullable(rows.get(call.<UUID>getArgument(0))));
    }

    @Test
    void publishesPendingRowsInOrderWithHeadersAndMarksThemPublished() {
        OutboxEventJpaEntity first = row("CONS-1", 0);
        OutboxEventJpaEntity second = row("CONS-1", 1);
        second.setTraceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        batch(first, second);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(sent());

        assertThat(relay.relayOnce()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> records = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(2)).send(records.capture());
        ProducerRecord<String, String> record = records.getAllValues().getFirst();
        assertThat(record.topic()).isEqualTo("evt.pay.rtp.created.v1");
        assertThat(record.key()).isEqualTo("CONS-1");
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("Payments.PayRequest.Created.v1");
        assertThat(new String(record.headers().lastHeader("x-fapi-interaction-id").value(), StandardCharsets.UTF_8))
                .isEqualTo("ix-1");
        assertThat(record.headers().lastHeader("traceparent")).isNull();
        assertThat(new String(records.getAllValues().get(1).headers().lastHeader("traceparent").value(),
                StandardCharsets.UTF_8)).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(first.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PUBLISHED);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getAttempts()).isEqualTo(1);
        assertThat(lock.released).isTrue();
    }

    @Test
    void anotherReplicaHoldingTheLockMeansNothingIsSent() {
        lock.available = false;

        assertThat(relay.relayOnce()).isZero();
        verify(outbox, never()).findPendingBatch(anyInt());
    }

    @Test
    void noDatabaseTransactionIsOpenWhileASendIsInFlight() {
        batch(row("CONS-1", 0), row("CONS-2", 0));
        List<Boolean> transactionOpenDuringSend = new ArrayList<>();
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(call -> {
            transactionOpenDuringSend.add(transactions.open.get());
            return sent();
        });

        assertThat(relay.relayOnce()).isEqualTo(2);

        assertThat(transactionOpenDuringSend).containsExactly(false, false);
        // one short transaction to read the batch, one per recorded outcome
        assertThat(transactions.count.get()).isEqualTo(3);
    }

    @Test
    void aSendThatNeverCompletesStopsTheBatchWithoutMarkingAnything() {
        relay = relay(Duration.ofMillis(50), Duration.ofSeconds(10));
        OutboxEventJpaEntity stuck = row("CONS-1", 0);
        OutboxEventJpaEntity sameAggregateLater = row("CONS-1", 1);
        OutboxEventJpaEntity otherAggregate = row("CONS-2", 0);
        batch(stuck, sameAggregateLater, otherAggregate);
        List<Boolean> transactionOpenDuringSend = new ArrayList<>();
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(call -> {
            transactionOpenDuringSend.add(transactions.open.get());
            return new CompletableFuture<>();
        });

        assertThat(relay.relayOnce()).isZero();

        assertThat(transactionOpenDuringSend).containsExactly(false);
        assertThat(stuck.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);
        assertThat(stuck.getAttempts()).isZero();
        assertThat(stuck.getLastError()).isNull();
        assertThat(failures("TimeoutException")).isEqualTo(1.0);
        // only the batch read ran in a transaction; nothing was written
        assertThat(transactions.count.get()).isEqualTo(1);
        assertThat(sameAggregateLater.getAttempts()).isZero();
        assertThat(otherAggregate.getAttempts()).isZero();
        verify(kafka, times(1)).send(any(ProducerRecord.class));
        assertThat(relay.consecutiveFailedRuns()).isEqualTo(1);
    }

    @Test
    void afterAFailedRunTheRelayBacksOffAndResetsOnSuccess() {
        OutboxEventJpaEntity row = row("CONS-1", 0);
        batch(row);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")))
                .thenReturn(sent());

        relay.relayOnce();                       // fails; next run not before +1 s
        relay.relayOnce();                       // skipped: backing off
        verify(kafka, times(1)).send(any(ProducerRecord.class));

        clock.advance(Duration.ofSeconds(1));
        relay.relayOnce();                       // fails again; next run not before +2 s
        assertThat(relay.consecutiveFailedRuns()).isEqualTo(2);
        clock.advance(Duration.ofSeconds(1));
        relay.relayOnce();                       // still backing off
        verify(kafka, times(2)).send(any(ProducerRecord.class));

        clock.advance(Duration.ofSeconds(1));
        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(relay.consecutiveFailedRuns()).isZero();
    }

    @Test
    void aRetryableErrorNeverParksNoMatterHowLongItLasts() {
        OutboxEventJpaEntity head = row("CONS-1", 0);
        OutboxEventJpaEntity later = row("CONS-1", 1);
        batch(head, later);
        when(kafka.send(any(ProducerRecord.class)))
                .thenAnswer(call -> CompletableFuture.failedFuture(new TimeoutException("broker unreachable")));

        for (int run = 0; run < 100; run++) {
            relay.relayOnce();
            clock.advance(Duration.ofHours(1)); // 100 h of continuous failure
        }

        assertThat(head.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);
        assertThat(head.getAttempts()).isZero();
        assertThat(later.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);
        verify(kafka, times(100)).send(any(ProducerRecord.class));
        assertThat(failures("TimeoutException")).isEqualTo(100.0);
    }

    static Stream<RuntimeException> payloadErrors() {
        return Stream.of(new RecordTooLargeException("record too large"),
                new SerializationException("cannot serialise"),
                new InvalidTopicException("invalid topic"));
    }

    @ParameterizedTest
    @MethodSource("payloadErrors")
    void aPayloadErrorParksTheRowAtOnceAndKeepsItsAggregateBlocked(RuntimeException payloadError) {
        OutboxEventJpaEntity poison = row("CONS-1", 0);
        OutboxEventJpaEntity sameAggregateLater = row("CONS-1", 1);
        OutboxEventJpaEntity otherAggregate = row("CONS-2", 0);
        batch(poison, sameAggregateLater, otherAggregate);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(payloadError))
                .thenReturn(sent());

        assertThat(relay.relayOnce()).isEqualTo(1);

        assertThat(poison.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PARKED);
        assertThat(poison.getAttempts()).isEqualTo(1);
        assertThat(poison.getParkedAt()).isEqualTo(NOW);
        assertThat(poison.getParkReason()).isEqualTo("payload error (relay)");
        assertThat(poison.getLastError()).startsWith(payloadError.getClass().getSimpleName());
        assertThat(failures(payloadError.getClass().getSimpleName())).isEqualTo(1.0);
        assertThat(meters.get("outbox.parked.events").tag("exception", payloadError.getClass().getSimpleName())
                .counter().count()).isEqualTo(1.0);
        assertThat(meters.get("outbox.parked.events").counter().getId().getTags())
                .extracting(io.micrometer.core.instrument.Tag::getKey).containsExactly("exception");
        assertThat(sameAggregateLater.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);
        assertThat(sameAggregateLater.getAttempts()).isZero();
        assertThat(otherAggregate.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PUBLISHED);
    }

    static Stream<RuntimeException> stopErrors() {
        return Stream.of(new SaslAuthenticationException("IAM authentication failed"),
                new TopicAuthorizationException("not authorised"),
                new KafkaException("Failed to construct kafka producer"));
    }

    @ParameterizedTest
    @MethodSource("stopErrors")
    void anAuthOrUnclassifiedErrorStopsTheBatchWithoutCountingOrParking(RuntimeException failure) {
        OutboxEventJpaEntity first = row("CONS-1", 0);
        OutboxEventJpaEntity otherAggregate = row("CONS-2", 0);
        batch(first, otherAggregate);
        when(kafka.send(any(ProducerRecord.class))).thenThrow(failure);

        assertThat(relay.relayOnce()).isZero();

        assertThat(first.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);
        assertThat(first.getAttempts()).isZero();
        assertThat(first.getLastError()).isNull();
        assertThat(otherAggregate.getAttempts()).isZero();
        assertThat(failures(failure.getClass().getSimpleName())).isEqualTo(1.0);
        assertThat(meters.find("outbox.parked.events").counter()).isNull();
        verify(kafka, times(1)).send(any(ProducerRecord.class));
        assertThat(lock.released).isTrue();
        assertThat(relay.consecutiveFailedRuns()).isEqualTo(1);
    }

    @Test
    void anInterruptedSendStopsTheBatchWithoutCounting() throws Exception {
        OutboxEventJpaEntity first = row("CONS-1", 0);
        batch(first, row("CONS-2", 0));
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> interrupted = mock(CompletableFuture.class);
        when(interrupted.get(any(Long.class), any())).thenThrow(new InterruptedException());
        when(kafka.send(any(ProducerRecord.class))).thenReturn(interrupted);

        assertThat(relay.relayOnce()).isZero();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(first.getAttempts()).isZero();
        verify(kafka, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void theRunStopsStartingSendsOnceItsTimeBudgetIsUsed() {
        relay = relay(Duration.ofSeconds(1), Duration.ofSeconds(10));
        batch(row("CONS-1", 0), row("CONS-2", 0), row("CONS-3", 0), row("CONS-4", 0));
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(call -> {
            clock.advance(Duration.ofSeconds(4));
            return sent();
        });

        // 0 s, 4 s and 8 s start a send; at 12 s the 10 s budget is used up
        assertThat(relay.relayOnce()).isEqualTo(3);
        verify(kafka, times(3)).send(any(ProducerRecord.class));
    }

    @Test
    void purgeDeletesPublishedRowsOlderThanRetention() {
        when(outbox.deletePublishedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(4);

        assertThat(relay.purgePublished()).isEqualTo(4);
    }

    @Test
    void settingsMustBePositive() {
        assertThatThrownBy(() -> new OutboxRelay.Settings(0, Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxRelay.Settings(10, Duration.ofSeconds(1), Duration.ZERO,
                Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFailureCounterIsTaggedByExceptionClassOnly() {
        batch(row("CONS-SECRET-1", 0));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")));

        relay.relayOnce();

        assertThat(meters.getMeters()).hasSize(1);
        assertThat(meters.getMeters().getFirst().getId().getTags())
                .extracting(io.micrometer.core.instrument.Tag::getKey).containsExactly("exception");
    }

    @Test
    void failuresAreClassifiedByTheirCause() {
        assertThat(OutboxRelay.classify(new java.util.concurrent.ExecutionException(new RecordTooLargeException("x"))))
                .isEqualTo(OutboxRelay.Failure.PAYLOAD);
        assertThat(OutboxRelay.classify(new java.util.concurrent.TimeoutException()))
                .isEqualTo(OutboxRelay.Failure.STOP);
        assertThat(OutboxRelay.classify(new java.util.concurrent.ExecutionException(new TimeoutException("x"))))
                .isEqualTo(OutboxRelay.Failure.STOP);
        assertThat(OutboxRelay.classify(new IllegalStateException("unknown")))
                .isEqualTo(OutboxRelay.Failure.STOP);
    }

    private OutboxRelay relay(Duration sendTimeout, Duration runBudget) {
        return new OutboxRelay(outbox, kafka, transactions, lock, clock, meters,
                new OutboxRelay.Settings(100, sendTimeout, runBudget, Duration.ofDays(7)));
    }

    private double failures(String exception) {
        return meters.get("outbox.send.failures").tag("exception", exception).counter().count();
    }

    private void batch(OutboxEventJpaEntity... pending) {
        for (OutboxEventJpaEntity row : pending) {
            rows.put(row.getEventId(), row);
        }
        when(outbox.findPendingBatch(100)).thenAnswer(call -> Stream.of(pending)
                .filter(r -> r.getStatus() == OutboxEventJpaEntity.Status.PENDING).toList());
    }

    private static CompletableFuture<SendResult<String, String>> sent() {
        return CompletableFuture.completedFuture(null);
    }

    private static OutboxEventJpaEntity row(String aggregateId, long version) {
        return new OutboxEventJpaEntity(UUID.randomUUID(), "PayRequest", aggregateId, version,
                "Payments.PayRequest.Created.v1", "evt.pay.rtp.created.v1", "{}", "ix-1", NOW.minusSeconds(5));
    }

    /** Counts transactions and says whether one is open right now. */
    static final class RecordingTransactions implements TransactionOperations {
        final AtomicBoolean open = new AtomicBoolean();
        final AtomicInteger count = new AtomicInteger();

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            TransactionStatus status = new SimpleTransactionStatus();
            open.set(true);
            count.incrementAndGet();
            try {
                return action.doInTransaction(status);
            } finally {
                open.set(false);
            }
        }
    }

    static final class FakeLock implements RelayLock {
        boolean available = true;
        boolean released;

        @Override
        public Optional<Held> tryAcquire() {
            if (!available) {
                return Optional.empty();
            }
            released = false;
            return Optional.of(() -> released = true);
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        void set(Instant at) {
            now = at;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
