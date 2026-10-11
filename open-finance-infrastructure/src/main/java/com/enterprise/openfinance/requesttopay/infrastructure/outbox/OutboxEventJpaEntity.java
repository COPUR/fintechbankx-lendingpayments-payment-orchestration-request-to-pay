package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of sc_pay_request_to_pay.outbox_event: one envelope waiting to be
 * relayed to Kafka. Written in the pay request's transaction.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEventJpaEntity {

    public enum Status { PENDING, PUBLISHED, PARKED }

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64, updatable = false)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private long aggregateVersion;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    /** The aggregate topic evt.pay.rtp.v1 (V7); the relay computes it and does not read this column. */
    @Column(name = "topic", nullable = false, length = 249, updatable = false)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "correlation_id", nullable = false, length = 128, updatable = false)
    private String correlationId;

    /** W3C trace context of the request that wrote the row, sent as the traceparent header. */
    @Column(name = "traceparent", length = 55, updatable = false)
    private String traceparent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "parked_at")
    private Instant parkedAt;

    @Column(name = "park_reason", length = 256)
    private String parkReason;

    /** True once this park was counted in outbox_parked_events_total (V4). */
    @Column(name = "park_counted", nullable = false)
    private boolean parkCounted;

    protected OutboxEventJpaEntity() {
    }

    public OutboxEventJpaEntity(UUID eventId, String aggregateType, String aggregateId, long aggregateVersion,
                                String eventType, String topic, String payload, String correlationId,
                                Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public UUID getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getEventType() { return eventType; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceparent() { return traceparent; }

    void setTraceparent(String traceparent) {
        this.traceparent = traceparent;
    }
    public Instant getOccurredAt() { return occurredAt; }
    public Status getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public String getParkReason() { return parkReason; }
    public Instant getParkedAt() { return parkedAt; }
    public boolean isParkCounted() { return parkCounted; }

    void markPublished(Instant at) {
        this.status = Status.PUBLISHED;
        this.publishedAt = at;
        this.attempts++;
        this.lastError = null;
    }

    /**
     * Dead-letter state for a payload error: waits for an operator and keeps the
     * aggregate's later rows pending. Operators park other rows by hand (runbook).
     */
    void park(String error, String reason, Instant at) {
        this.attempts++;
        this.lastError = truncate(error);
        this.status = Status.PARKED;
        this.parkedAt = at;
        this.parkReason = reason;
        this.parkCounted = true;
    }

    /** An operator park (runbook SQL) has been counted. */
    void markParkCounted() {
        this.parkCounted = true;
    }

    private static String truncate(String error) {
        return error == null ? null : error.substring(0, Math.min(error.length(), 512));
    }
}
