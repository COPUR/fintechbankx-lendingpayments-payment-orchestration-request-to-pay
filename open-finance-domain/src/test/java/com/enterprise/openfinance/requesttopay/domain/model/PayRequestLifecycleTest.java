package com.enterprise.openfinance.requesttopay.domain.model;

import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestCreatedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aggregate registers one event per state change and counts its own
 * version; the outbox uses both (event payload, envelope aggregateVersion).
 */
class PayRequestLifecycleTest {

    private static final Instant REQUESTED = Instant.parse("2026-02-10T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-02-10T10:00:01Z");
    private static final Instant DECIDED = Instant.parse("2026-02-10T10:05:00Z");

    @Test
    void createRegistersCreatedEventWithTheRequestedFigures() {
        PayRequest request = PayRequest.create("CONS-RTP-1", command("500.00", "AED"), CREATED);

        assertThat(request.status()).isEqualTo(PayRequestStatus.AWAITING_AUTHORISATION);
        assertThat(request.version()).isZero();
        assertThat(request.requestedAt()).isEqualTo(REQUESTED);
        assertThat(request.updatedAt()).isEqualTo(CREATED);
        assertThat(request.domainEvents()).singleElement().isInstanceOfSatisfying(PayRequestCreatedEvent.class, e -> {
            assertThat(e.aggregateId()).isEqualTo("CONS-RTP-1");
            assertThat(e.amount()).isEqualByComparingTo("500.00");
            assertThat(e.currency()).isEqualTo("AED");
            assertThat(e.creditorName()).isEqualTo("Utilities Co");
            assertThat(e.debtorId()).isEqualTo("PSU-001");
            assertThat(e.occurredOn()).isEqualTo(CREATED);
        });
    }

    @Test
    void consumeRegistersAcceptedEventAndBumpsVersion() {
        PayRequest consumed = PayRequest.create("CONS-RTP-1", command("500.00", "AED"), CREATED)
                .consume("PAY-77", new DecisionBy("TPP-001", null), DECIDED);

        assertThat(consumed.version()).isEqualTo(1);
        assertThat(consumed.domainEvents()).singleElement().isInstanceOfSatisfying(PayRequestAcceptedEvent.class, e -> {
            assertThat(e.aggregateId()).isEqualTo("CONS-RTP-1");
            assertThat(e.paymentId()).isEqualTo("PAY-77");
            assertThat(e.amount()).isEqualByComparingTo("500.00");
            assertThat(e.occurredOn()).isEqualTo(DECIDED);
        });
    }

    @Test
    void rejectRegistersRejectedEventAndBumpsVersion() {
        PayRequest rejected = PayRequest.create("CONS-RTP-1", command("500.00", "AED"), CREATED).reject(new DecisionBy("TPP-001", null), DECIDED);

        assertThat(rejected.version()).isEqualTo(1);
        assertThat(rejected.domainEvents()).singleElement().isInstanceOfSatisfying(PayRequestRejectedEvent.class,
                e -> assertThat(e.occurredOn()).isEqualTo(DECIDED));
    }

    @Test
    void finalizedRequestRejectsEveryFurtherDecisionWithTheDomainException() {
        PayRequest consumed = PayRequest.create("CONS-RTP-1", command("500.00", "AED"), CREATED).consume("PAY-77", new DecisionBy("TPP-001", null), DECIDED);
        PayRequest rejected = PayRequest.create("CONS-RTP-2", command("500.00", "AED"), CREATED).reject(new DecisionBy("TPP-001", null), DECIDED);

        assertThatThrownBy(() -> consumed.reject(new DecisionBy("TPP-001", null), DECIDED)).isInstanceOf(PayRequestFinalizedException.class);
        assertThatThrownBy(() -> consumed.consume("PAY-78", new DecisionBy("TPP-001", null), DECIDED)).isInstanceOf(PayRequestFinalizedException.class);
        assertThatThrownBy(() -> rejected.consume("PAY-78", new DecisionBy("TPP-001", null), DECIDED)).isInstanceOf(PayRequestFinalizedException.class);
    }

    @Test
    void consumeRequiresThePaymentId() {
        PayRequest request = PayRequest.create("CONS-RTP-1", command("500.00", "AED"), CREATED);

        assertThatThrownBy(() -> request.consume(" ", new DecisionBy("TPP-001", null), DECIDED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("paymentId");
    }

    @Test
    void amountMayNotHaveMoreDecimalsThanTheCurrencyAllows() {
        // AED and USD have 2 minor digits, BHD 3, JPY none.
        assertThat(PayRequest.create("C1", command("500.10", "AED"), CREATED).amount()).isEqualByComparingTo("500.10");
        assertThat(PayRequest.create("C2", command("500.1000", "AED"), CREATED).amount()).isEqualByComparingTo("500.10");
        assertThat(PayRequest.create("C3", command("12.345", "BHD"), CREATED).amount()).isEqualByComparingTo("12.345");
        assertThatThrownBy(() -> PayRequest.create("C4", command("500.105", "AED"), CREATED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AED allows 2 decimal places");
        assertThatThrownBy(() -> PayRequest.create("C5", command("100.5", "JPY"), CREATED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPY allows 0 decimal places");
    }

    @Test
    void currencyMustBeAnIso4217Code() {
        assertThatThrownBy(() -> PayRequest.create("C1", command("1.00", "XXQ"), CREATED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO 4217");
        assertThatThrownBy(() -> PayRequest.create("C1", command("1.00", "AEDX"), CREATED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO 4217");
    }

    @Test
    void rehydratedRequestCarriesNoPendingEvents() {
        PayRequest stored = new PayRequest("CONS-1", "TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500.00"),
                "AED", PayRequestStatus.CONSUMED, REQUESTED, DECIDED, "PAY-1", 1L);

        assertThat(stored.domainEvents()).isEmpty();
        assertThat(stored.version()).isEqualTo(1);
    }

    @Test
    void consumedRequestMustCarryAPaymentId() {
        assertThatThrownBy(() -> new PayRequest("CONS-1", "TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500.00"),
                "AED", PayRequestStatus.CONSUMED, REQUESTED, DECIDED, null, 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("paymentId");
    }

    private static CreatePayRequestCommand command(String amount, String currency) {
        return new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co", new BigDecimal(amount), currency,
                REQUESTED, "ix-1");
    }
}
