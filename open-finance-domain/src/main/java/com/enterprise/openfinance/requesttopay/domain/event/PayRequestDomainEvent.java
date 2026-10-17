package com.enterprise.openfinance.requesttopay.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A fact registered by the PayRequest aggregate. The public (Kafka) form is
 * built by the outbox adapter from the AsyncAPI contract svc-pay-request-to-pay.
 */
public sealed interface PayRequestDomainEvent
        permits PayRequestCreatedEvent, PayRequestAcceptedEvent, PayRequestRejectedEvent {

    UUID eventId();

    String aggregateId();

    Instant occurredOn();
}
