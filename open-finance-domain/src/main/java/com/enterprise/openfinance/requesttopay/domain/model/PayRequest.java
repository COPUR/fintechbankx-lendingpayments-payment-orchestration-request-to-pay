package com.enterprise.openfinance.requesttopay.domain.model;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestCreatedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestDomainEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * PayRequest aggregate (immutable). Every state change returns a new
 * instance whose {@link #version()} is one higher and whose
 * {@link #domainEvents()} holds the event of that change; instances loaded
 * from storage carry no events.
 *
 * @param version number of state changes since creation (0 when created);
 *                published as the envelope aggregateVersion
 */
public record PayRequest(
        String consentId,
        String tppId,
        String psuId,
        String creditorName,
        BigDecimal amount,
        String currency,
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
        currency = requireIsoCurrency(currency);
        amount = requireAmount(amount, currency);
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
        this(consentId, tppId, psuId, creditorName, amount, currency, status, requestedAt, updatedAt, paymentId,
                version, List.of());
    }

    /** Rehydrates a stored pay request at version 0 (no pending events). */
    public PayRequest(String consentId, String tppId, String psuId, String creditorName, BigDecimal amount,
                      String currency, PayRequestStatus status, Instant requestedAt, Instant updatedAt,
                      String paymentId) {
        this(consentId, tppId, psuId, creditorName, amount, currency, status, requestedAt, updatedAt, paymentId, 0L);
    }

    /** A new request awaiting the debtor's authorisation; registers PayRequestCreatedEvent. */
    public static PayRequest create(String consentId, CreatePayRequestCommand command, Instant now) {
        PayRequest draft = new PayRequest(consentId, command.tppId(), command.psuId(), command.creditorName(),
                command.amount(), command.currency(), PayRequestStatus.AWAITING_AUTHORISATION,
                command.requestedAt(), now, null, 0L);
        return draft.withEvent(new PayRequestCreatedEvent(draft.consentId, draft.creditorName, draft.amount,
                draft.currency, draft.psuId, now));
    }

    public boolean belongsTo(String tppIdValue) {
        return tppId.equals(tppIdValue);
    }

    public boolean isFinalized() {
        return status.isFinal();
    }

    /** The debtor declined; registers PayRequestRejectedEvent. */
    public PayRequest reject(Instant now) {
        ensureNotFinalized();
        PayRequest next = new PayRequest(consentId, tppId, psuId, creditorName, amount, currency,
                PayRequestStatus.REJECTED, requestedAt, now, paymentId, version + 1);
        return next.withEvent(new PayRequestRejectedEvent(consentId, now));
    }

    /** The debtor accepted and a payment was created; registers PayRequestAcceptedEvent. */
    public PayRequest consume(String paymentIdValue, Instant now) {
        ensureNotFinalized();
        String resolvedPaymentId = requireNotBlank(paymentIdValue, "paymentId");
        PayRequest next = new PayRequest(consentId, tppId, psuId, creditorName, amount, currency,
                PayRequestStatus.CONSUMED, requestedAt, now, resolvedPaymentId, version + 1);
        return next.withEvent(new PayRequestAcceptedEvent(consentId, resolvedPaymentId, amount, currency,
                creditorName, psuId, now));
    }

    public Optional<String> paymentIdOptional() {
        return Optional.ofNullable(paymentId);
    }

    private PayRequest withEvent(PayRequestDomainEvent event) {
        return new PayRequest(consentId, tppId, psuId, creditorName, amount, currency, status, requestedAt,
                updatedAt, paymentId, version, List.of(event));
    }

    private void ensureNotFinalized() {
        if (isFinalized()) {
            throw new PayRequestFinalizedException("Pay request already finalized");
        }
    }

    private static String requireIsoCurrency(String value) {
        String code = requireNotBlank(value, "currency").toUpperCase(java.util.Locale.ROOT);
        try {
            if (code.length() == 3 && Currency.getInstance(code).getDefaultFractionDigits() >= 0) {
                return code;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        throw new IllegalArgumentException("currency must be an ISO 4217 code: " + code);
    }

    /** Positive, with no more decimals than the currency's minor unit (trailing zeros ignored). */
    private static BigDecimal requireAmount(BigDecimal value, String currency) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        int minorDigits = Currency.getInstance(currency).getDefaultFractionDigits();
        BigDecimal normalised = value.stripTrailingZeros();
        if (normalised.scale() > minorDigits) {
            throw new IllegalArgumentException(
                    "amount " + value.toPlainString() + ": " + currency + " allows " + minorDigits + " decimal places");
        }
        return normalised.setScale(minorDigits);
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
