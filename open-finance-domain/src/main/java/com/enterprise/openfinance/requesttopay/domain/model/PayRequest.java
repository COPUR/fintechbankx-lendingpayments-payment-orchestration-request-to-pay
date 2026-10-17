package com.enterprise.openfinance.requesttopay.domain.model;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestCreatedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestDomainEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import com.enterprise.openfinance.requesttopay.domain.model.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PayRequest aggregate (immutable). Every state change returns a new
 * instance whose {@link #version()} is one higher and whose
 * {@link #domainEvents()} holds the event of that change; instances loaded
 * from storage carry no events. The amount is a {@link Money} value object.
 *
 * @param version number of state changes since creation (0 when created);
 *                published as the envelope aggregateVersion
 */
public record PayRequest(
        String consentId,
        String tppId,
        String psuId,
        String creditorName,
        Money money,
        PayRequestStatus status,
        Instant requestedAt,
        Instant updatedAt,
        String paymentId,
        long version,
        List<PayRequestDomainEvent> domainEvents
) {

    public PayRequest {
        consentId = requireNotBlank(consentId, "consentId");
        tppId = requireNotBlank(tppId, "tppId");
        psuId = requireNotBlank(psuId, "psuId");
        creditorName = requireNotBlank(creditorName, "creditorName");
        if (money == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }
        if (status == PayRequestStatus.CONSUMED && (paymentId == null || paymentId.isBlank())) {
            throw new IllegalArgumentException("paymentId is required for a consumed pay request");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        domainEvents = domainEvents == null ? List.of() : List.copyOf(domainEvents);
    }

    /** Rehydrates a stored pay request (no pending events). */
    public PayRequest(String consentId, String tppId, String psuId, String creditorName, BigDecimal amount,
                      String currency, PayRequestStatus status, Instant requestedAt, Instant updatedAt,
                      String paymentId, long version) {
        this(consentId, tppId, psuId, creditorName, new Money(amount, currency), status, requestedAt, updatedAt,
                paymentId, version, List.of());
    }

    /** Rehydrates a stored pay request at version 0 (no pending events). */
    public PayRequest(String consentId, String tppId, String psuId, String creditorName, BigDecimal amount,
                      String currency, PayRequestStatus status, Instant requestedAt, Instant updatedAt,
                      String paymentId) {
        this(consentId, tppId, psuId, creditorName, amount, currency, status, requestedAt, updatedAt, paymentId, 0L);
    }

    /** A new request awaiting the decision; registers PayRequestCreatedEvent. */
    public static PayRequest create(String consentId, CreatePayRequestCommand command, Instant now) {
        PayRequest draft = new PayRequest(consentId, command.tppId(), command.psuId(), command.creditorName(),
                command.amount(), command.currency(), PayRequestStatus.AWAITING_AUTHORISATION,
                command.requestedAt(), now, null, 0L);
        return draft.withEvent(new PayRequestCreatedEvent(draft.consentId, draft.creditorName, draft.amount(),
                draft.currency(), draft.psuId, now));
    }

    public boolean belongsTo(String tppIdValue) {
        return tppId.equals(tppIdValue);
    }

    public boolean isFinalized() {
        return status.isFinal();
    }

    /**
     * The requesting TPP reported that the debtor declined; registers PayRequestRejectedEvent.
     * Repeating a reject on a rejected request changes nothing and registers no event
     * (a retried call gets the current state).
     */
    public PayRequest reject(DecisionBy decision, Instant now) {
        requireDecision(decision);
        if (status == PayRequestStatus.REJECTED) {
            return withoutEvents();
        }
        ensureNotFinalized();
        PayRequest next = new PayRequest(consentId, tppId, psuId, creditorName, money,
                PayRequestStatus.REJECTED, requestedAt, now, paymentId, version + 1, List.of());
        return next.withEvent(new PayRequestRejectedEvent(consentId, decision.actorClientId(), decision.reason(), now));
    }

    /**
     * The requesting TPP reported acceptance with {@code paymentIdValue} (not verified against a
     * payment); registers PayRequestAcceptedEvent. Repeating the accept with the same paymentId
     * changes nothing and registers no event; another paymentId is refused.
     */
    public PayRequest consume(String paymentIdValue, DecisionBy decision, Instant now) {
        requireDecision(decision);
        String resolvedPaymentId = requireNotBlank(paymentIdValue, "paymentId");
        if (status == PayRequestStatus.CONSUMED && resolvedPaymentId.equals(paymentId)) {
            return withoutEvents();
        }
        ensureNotFinalized();
        PayRequest next = new PayRequest(consentId, tppId, psuId, creditorName, money,
                PayRequestStatus.CONSUMED, requestedAt, now, resolvedPaymentId, version + 1, List.of());
        return next.withEvent(new PayRequestAcceptedEvent(consentId, resolvedPaymentId, amount(), currency(),
                creditorName, psuId, decision.actorClientId(), decision.reason(), now));
    }

    /** Requested amount at the currency's minor unit. */
    public BigDecimal amount() {
        return money.amount();
    }

    /** ISO 4217 currency code. */
    public String currency() {
        return money.currency();
    }

    public Optional<String> paymentIdOptional() {
        return Optional.ofNullable(paymentId);
    }

    private PayRequest withEvent(PayRequestDomainEvent event) {
        return new PayRequest(consentId, tppId, psuId, creditorName, money, status, requestedAt,
                updatedAt, paymentId, version, List.of(event));
    }

    private PayRequest withoutEvents() {
        return new PayRequest(consentId, tppId, psuId, creditorName, money, status, requestedAt,
                updatedAt, paymentId, version, List.of());
    }

    private static void requireDecision(DecisionBy decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
    }

    private void ensureNotFinalized() {
        if (isFinalized()) {
            throw new PayRequestFinalizedException("Pay request already finalized");
        }
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
