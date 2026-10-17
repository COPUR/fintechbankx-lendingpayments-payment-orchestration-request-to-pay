package com.enterprise.openfinance.requesttopay.domain.model;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A decision names who decided (the deciding client) and optionally why, and a repeated
 * decision with the same outcome is answered with the current state instead of an error,
 * so a TPP that lost the response can retry safely.
 */
class PayRequestDecisionTest {

    private static final Instant CREATED = Instant.parse("2026-02-10T10:00:01Z");
    private static final Instant DECIDED = Instant.parse("2026-02-10T10:05:00Z");
    private static final Instant RETRIED = Instant.parse("2026-02-10T10:05:30Z");
    private static final DecisionBy BY_TPP = new DecisionBy("TPP-001", null);

    @Test
    void acceptedEventCarriesTheDecidingClientAndTheReason() {
        PayRequest consumed = created().consume("PAY-77", new DecisionBy("TPP-001", "paid by card"), DECIDED);

        assertThat(consumed.domainEvents()).singleElement().isInstanceOfSatisfying(PayRequestAcceptedEvent.class, e -> {
            assertThat(e.actorClientId()).isEqualTo("TPP-001");
            assertThat(e.reason()).isEqualTo("paid by card");
        });
    }

    @Test
    void rejectedEventCarriesTheDecidingClientAndTheReason() {
        PayRequest rejected = created().reject(new DecisionBy("TPP-001", "debtor declined"), DECIDED);

        assertThat(rejected.domainEvents()).singleElement().isInstanceOfSatisfying(PayRequestRejectedEvent.class, e -> {
            assertThat(e.actorClientId()).isEqualTo("TPP-001");
            assertThat(e.reason()).isEqualTo("debtor declined");
        });
    }

    @Test
    void repeatingAnAcceptWithTheSamePaymentIdChangesNothing() {
        PayRequest consumed = withoutEvents(created().consume("PAY-77", BY_TPP, DECIDED));

        PayRequest again = consumed.consume(" PAY-77 ", BY_TPP, RETRIED);

        assertThat(again.status()).isEqualTo(PayRequestStatus.CONSUMED);
        assertThat(again.paymentId()).isEqualTo("PAY-77");
        assertThat(again.version()).isEqualTo(1);
        assertThat(again.updatedAt()).isEqualTo(DECIDED);
        assertThat(again.domainEvents()).isEmpty();
    }

    @Test
    void anAcceptWithAnotherPaymentIdOnAConsumedRequestIsRefused() {
        PayRequest consumed = withoutEvents(created().consume("PAY-77", BY_TPP, DECIDED));

        assertThatThrownBy(() -> consumed.consume("PAY-78", BY_TPP, RETRIED))
                .isInstanceOf(PayRequestFinalizedException.class);
    }

    @Test
    void repeatingARejectChangesNothing() {
        PayRequest rejected = withoutEvents(created().reject(BY_TPP, DECIDED));

        PayRequest again = rejected.reject(new DecisionBy("TPP-001", "again"), RETRIED);

        assertThat(again.status()).isEqualTo(PayRequestStatus.REJECTED);
        assertThat(again.version()).isEqualTo(1);
        assertThat(again.updatedAt()).isEqualTo(DECIDED);
        assertThat(again.domainEvents()).isEmpty();
    }

    @Test
    void theOppositeDecisionOnAFinalRequestIsRefused() {
        PayRequest consumed = withoutEvents(created().consume("PAY-77", BY_TPP, DECIDED));
        PayRequest rejected = withoutEvents(created().reject(BY_TPP, DECIDED));

        assertThatThrownBy(() -> consumed.reject(BY_TPP, RETRIED)).isInstanceOf(PayRequestFinalizedException.class);
        assertThatThrownBy(() -> rejected.consume("PAY-77", BY_TPP, RETRIED))
                .isInstanceOf(PayRequestFinalizedException.class);
    }

    @Test
    void theDecidingClientIsRequiredAndTheReasonIsShortOptionalText() {
        assertThatThrownBy(() -> new DecisionBy(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecisionBy("TPP-001", "x".repeat(257))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DecisionBy(" TPP-001 ", "  ").reason()).isNull();
        assertThat(new DecisionBy(" TPP-001 ", " because ")).isEqualTo(new DecisionBy("TPP-001", "because"));
        assertThat(new DecisionBy("TPP-001", "x".repeat(256)).reason()).hasSize(256);
    }

    private static PayRequest created() {
        return PayRequest.create("CONS-RTP-1", new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co",
                new BigDecimal("500.00"), "AED", CREATED, "ix-1"), CREATED);
    }

    /** As loaded from storage: same state, no pending events. */
    private static PayRequest withoutEvents(PayRequest request) {
        return new PayRequest(request.consentId(), request.tppId(), request.psuId(), request.creditorName(),
                request.amount(), request.currency(), request.status(), request.requestedAt(), request.updatedAt(),
                request.paymentId(), request.version());
    }
}
