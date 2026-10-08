package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        relay = new OutboxRelay(outbox, kafka, new TransactionTemplate(txManager), Clock.fixed(NOW, ZoneOffset.UTC),
                100, 3, Duration.ofSeconds(1), Duration.ofDays(7));
    }

    @Test
    void publishesPendingRowsInOrderWithHeadersAndMarksThemPublished() {
        OutboxEventJpaEntity first = row("CONS-1", 0);
        OutboxEventJpaEntity second = row("CONS-1", 1);
        second.setTraceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        when(outbox.tryRelayLock(anyLong())).thenReturn(true);
        when(outbox.findPendingBatch(100)).thenReturn(List.of(first, second));
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
    }

    @Test
    void anotherReplicaHoldingTheLockMeansNothingIsSent() {
        when(outbox.tryRelayLock(anyLong())).thenReturn(false);

        assertThat(relay.relayOnce()).isZero();
        verify(outbox, never()).findPendingBatch(anyInt());
    }

    @Test
    void failedSendHoldsBackThatAggregatesLaterEventsButNotOtherAggregates() {
        OutboxEventJpaEntity failing = row("CONS-1", 0);
        OutboxEventJpaEntity sameAggregateLater = row("CONS-1", 1);
        OutboxEventJpaEntity otherAggregate = row("CONS-2", 0);
        when(outbox.tryRelayLock(anyLong())).thenReturn(true);
        when(outbox.findPendingBatch(100)).thenReturn(List.of(failing, sameAggregateLater, otherAggregate));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .thenReturn(sent());

        assertThat(relay.relayOnce()).isEqualTo(1);

        assertThat(failing.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo("ExecutionException");
        assertThat(sameAggregateLater.getAttempts()).isZero();
        assertThat(otherAggregate.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PUBLISHED);
    }

    @Test
    void poisonRowIsParkedAfterMaxAttemptsAndNoLongerBlocksLaterRows() {
        OutboxEventJpaEntity poison = row("CONS-1", 0);
        OutboxEventJpaEntity later = row("CONS-1", 1);
        when(outbox.tryRelayLock(anyLong())).thenReturn(true);
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<String, String> record = inv.getArgument(0);
            String eventId = new String(record.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8);
            return eventId.equals(poison.getEventId().toString())
                    ? CompletableFuture.failedFuture(new IllegalArgumentException("record too large"))
                    : sent();
        });

        when(outbox.findPendingBatch(100)).thenReturn(List.of(poison, later));
        relay.relayOnce();
        relay.relayOnce();
        assertThat(later.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PENDING);

        // third failure parks the poison row; the later row goes out in the same run
        relay.relayOnce();
        assertThat(poison.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PARKED);
        assertThat(poison.getAttempts()).isEqualTo(3);
        assertThat(later.getStatus()).isEqualTo(OutboxEventJpaEntity.Status.PUBLISHED);
    }

    @Test
    void purgeDeletesPublishedRowsOlderThanRetention() {
        when(outbox.deletePublishedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(4);

        assertThat(relay.purgePublished()).isEqualTo(4);
    }

    @Test
    void maxAttemptsMustBePositive() {
        assertThatThrownBy(() -> new OutboxRelay(outbox, kafka, new TransactionTemplate(txManager), Clock.systemUTC(),
                10, 0, Duration.ofSeconds(1), Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static CompletableFuture<SendResult<String, String>> sent() {
        return CompletableFuture.completedFuture(null);
    }

    private static OutboxEventJpaEntity row(String aggregateId, long version) {
        return new OutboxEventJpaEntity(UUID.randomUUID(), "PayRequest", aggregateId, version,
                "Payments.PayRequest.Created.v1", "evt.pay.rtp.created.v1", "{}", "ix-1", NOW.minusSeconds(5));
    }
}
