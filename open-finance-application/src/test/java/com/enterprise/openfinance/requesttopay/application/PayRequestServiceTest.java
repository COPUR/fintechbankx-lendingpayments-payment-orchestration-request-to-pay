package com.enterprise.openfinance.requesttopay.application;

import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestAcceptedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestCreatedEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestDomainEvent;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestRejectedEvent;
import com.enterprise.openfinance.requesttopay.domain.exception.IdempotencyKeyConflictException;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestAccessDeniedException;
import com.enterprise.openfinance.requesttopay.domain.model.IdempotencyRecord;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.exception.ResourceNotFoundException;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestSettings;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestCachePort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestEventPublisher;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestIdempotencyPort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayRequestServiceTest {

    private final PayRequestRepositoryPort repositoryPort = mock(PayRequestRepositoryPort.class);
    private final PayRequestCachePort cachePort = mock(PayRequestCachePort.class);
    private final PayRequestEventPublisher eventPublisher = mock(PayRequestEventPublisher.class);
    private final PayRequestIdempotencyPort idempotencyPort = mock(PayRequestIdempotencyPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-02-10T10:00:00Z"), ZoneOffset.UTC);

    private final PayRequestService service = new PayRequestService(
            repositoryPort,
            cachePort,
            eventPublisher,
            idempotencyPort,
            new PayRequestSettings(Duration.ofMinutes(2), Duration.ofHours(24)),
            clock,
            () -> "CONS-REQ-001"
    );

    @Test
    void shouldCreatePayRequestAndNotify() {
        when(repositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePayRequestCommand command = new CreatePayRequestCommand(
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                Instant.parse("2026-02-10T10:00:00Z"),
                "ix-request-to-pay-1"
        );

        PayRequestResult result = service.createPayRequest(command);

        assertThat(result.status()).isEqualTo(PayRequestStatus.AWAITING_AUTHORISATION);
        ArgumentCaptor<PayRequest> published = ArgumentCaptor.forClass(PayRequest.class);
        verify(eventPublisher).publish(published.capture(), anyList(), eq("ix-request-to-pay-1"));
        assertThat(published.getValue().domainEvents()).singleElement().isInstanceOf(PayRequestCreatedEvent.class);
        verify(idempotencyPort, never()).reserve(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnCachedStatusOnHit() {
        PayRequest request = baseRequest();
        when(cachePort.getStatus("pay-request:CONS-001:TPP-001", Instant.parse("2026-02-10T10:00:00Z")))
                .thenReturn(Optional.of(new PayRequestResult(request, true)));

        PayRequestResult result = service.getPayRequestStatus(new GetPayRequestStatusQuery("CONS-001", "TPP-001", "ix"));

        assertThat(result.cacheHit()).isTrue();
        verify(repositoryPort, never()).findByConsentId("CONS-001");
    }

    @Test
    void shouldReadAndCacheOnMiss() {
        PayRequest request = baseRequest();
        when(cachePort.getStatus(any(), any())).thenReturn(Optional.empty());
        when(repositoryPort.findByConsentId("CONS-001")).thenReturn(Optional.of(request));

        PayRequestResult result = service.getPayRequestStatus(new GetPayRequestStatusQuery("CONS-001", "TPP-001", "ix"));

        assertThat(result.cacheHit()).isFalse();
        verify(cachePort).putStatus(
                "pay-request:CONS-001:TPP-001",
                result,
                Instant.parse("2026-02-10T10:02:00Z")
        );
    }

    @Test
    void shouldRejectPayRequestNotFound() {
        when(cachePort.getStatus(any(), any())).thenReturn(Optional.empty());
        when(repositoryPort.findByConsentId("CONS-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayRequestStatus(new GetPayRequestStatusQuery("CONS-404", "TPP-001", "ix")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pay request not found");
    }

    @Test
    void shouldRejectDuplicateFinalize() {
        PayRequest request = baseRequest().consume("PAY-001", new DecisionBy("TPP-001", null), Instant.parse("2026-02-10T11:00:00Z"));
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.rejectPayRequest("CONS-001", "TPP-001", null, "ix"))
                .isInstanceOf(PayRequestFinalizedException.class)
                .hasMessageContaining("finalized");
    }

    @Test
    void shouldRejectWrongOwner() {
        PayRequest request = baseRequest();
        when(repositoryPort.findByConsentId("CONS-001")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getPayRequestStatus(new GetPayRequestStatusQuery("CONS-001", "TPP-XYZ", "ix")))
                .isInstanceOf(PayRequestAccessDeniedException.class)
                .hasMessageContaining("participant");
    }

    @Test
    void shouldConsumePayRequest() {
        PayRequest request = baseRequest();
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(request));
        when(repositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PayRequestResult result = service.acceptPayRequest("CONS-001", "TPP-001", "PAY-123", null, "ix");

        assertThat(result.status()).isEqualTo(PayRequestStatus.CONSUMED);
        ArgumentCaptor<PayRequest> captor = ArgumentCaptor.forClass(PayRequest.class);
        verify(repositoryPort).save(captor.capture());
        assertThat(captor.getValue().paymentIdOptional()).contains("PAY-123");
    }

    @Test
    void consumePublishesAcceptedEventWithTheInteractionIdAsCorrelation() {
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(baseRequest()));
        when(repositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.acceptPayRequest("CONS-001", "TPP-001", "PAY-123", null, "ix-accept");

        ArgumentCaptor<List<PayRequestDomainEvent>> events = eventsCaptor();
        verify(eventPublisher).publish(any(), events.capture(), eq("ix-accept"));
        assertThat(events.getValue()).singleElement().isInstanceOfSatisfying(PayRequestAcceptedEvent.class,
                e -> assertThat(e.paymentId()).isEqualTo("PAY-123"));
        verify(cachePort).putStatus(eq("pay-request:CONS-001:TPP-001"), any(), eq(Instant.parse("2026-02-10T10:02:00Z")));
    }

    @Test
    void rejectPublishesRejectedEventAndStoresVersionOne() {
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(baseRequest()));
        when(repositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PayRequestResult result = service.rejectPayRequest("CONS-001", "TPP-001", null, "ix-reject");

        assertThat(result.status()).isEqualTo(PayRequestStatus.REJECTED);
        assertThat(result.request().version()).isEqualTo(1);
        ArgumentCaptor<List<PayRequestDomainEvent>> events = eventsCaptor();
        verify(eventPublisher).publish(any(), events.capture(), eq("ix-reject"));
        assertThat(events.getValue()).singleElement().isInstanceOf(PayRequestRejectedEvent.class);
    }

    @Test
    void repeatedAcceptWithTheSamePaymentIdReturnsTheCurrentStateWithoutSavingOrPublishing() {
        PayRequest consumed = new PayRequest("CONS-001", "TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500.00"),
                "AED", PayRequestStatus.CONSUMED, Instant.parse("2026-02-10T09:00:00Z"),
                Instant.parse("2026-02-10T09:30:00Z"), "PAY-123", 1L);
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(consumed));

        PayRequestResult result = service.acceptPayRequest("CONS-001", "TPP-001", "PAY-123", null, "ix-retry");

        assertThat(result.status()).isEqualTo(PayRequestStatus.CONSUMED);
        assertThat(result.request().version()).isEqualTo(1);
        verify(repositoryPort, never()).save(any());
        verify(eventPublisher, never()).publish(any(), anyList(), any());
    }

    @Test
    void repeatedRejectReturnsTheCurrentStateWithoutSavingOrPublishing() {
        PayRequest rejected = new PayRequest("CONS-001", "TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500.00"),
                "AED", PayRequestStatus.REJECTED, Instant.parse("2026-02-10T09:00:00Z"),
                Instant.parse("2026-02-10T09:30:00Z"), null, 1L);
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(rejected));

        PayRequestResult result = service.rejectPayRequest("CONS-001", "TPP-001", null, "ix-retry");

        assertThat(result.status()).isEqualTo(PayRequestStatus.REJECTED);
        verify(repositoryPort, never()).save(any());
        verify(eventPublisher, never()).publish(any(), anyList(), any());
    }

    @Test
    void theDecisionEventNamesTheDecidingClientAndTheReason() {
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(baseRequest()));
        when(repositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.rejectPayRequest("CONS-001", "TPP-001", "debtor declined", "ix-reject");

        ArgumentCaptor<List<PayRequestDomainEvent>> events = eventsCaptor();
        verify(eventPublisher).publish(any(), events.capture(), eq("ix-reject"));
        assertThat(events.getValue()).singleElement().isInstanceOfSatisfying(PayRequestRejectedEvent.class, e -> {
            assertThat(e.actorClientId()).isEqualTo("TPP-001");
            assertThat(e.reason()).isEqualTo("debtor declined");
        });
    }

    @Test
    void decisionOnUnknownRequestIsNotFoundAndPublishesNothing() {
        when(repositoryPort.findByConsentIdForUpdate("CONS-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptPayRequest("CONS-404", "TPP-001", "PAY-1", null, "ix"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(eventPublisher, never()).publish(any(), anyList(), any());
    }

    @Test
    void decisionByAnotherTppIsRefusedAndNothingIsSaved() {
        when(repositoryPort.findByConsentIdForUpdate("CONS-001")).thenReturn(Optional.of(baseRequest()));

        assertThatThrownBy(() -> service.rejectPayRequest("CONS-001", "TPP-XYZ", null, "ix"))
                .isInstanceOf(PayRequestAccessDeniedException.class).hasMessageContaining("participant");
        verify(repositoryPort, never()).save(any());
    }

    @Test
    void firstUseOfIdempotencyKeyReservesItForTheNewRequest() {
        when(repositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreatePayRequestCommand command = commandWithKey("500.00", "idem-1");
        when(idempotencyPort.reserve("TPP-001", "idem-1", command.fingerprint(), "CONS-REQ-001",
                Instant.parse("2026-02-10T10:00:00Z"), Instant.parse("2026-02-11T10:00:00Z")))
                .thenReturn(Optional.empty());

        PayRequestResult result = service.createPayRequest(command);

        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.request().consentId()).isEqualTo("CONS-REQ-001");
        verify(eventPublisher).publish(any(), anyList(), eq("ix-1"));
    }

    @Test
    void retryWithSameKeyAndPayloadReturnsTheOriginalRequestWithoutNewEvents() {
        CreatePayRequestCommand retry = commandWithKey("500.0", "idem-1");
        when(idempotencyPort.reserve(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new IdempotencyRecord("CONS-001", retry.fingerprint())));
        when(repositoryPort.findByConsentId("CONS-001")).thenReturn(Optional.of(baseRequest()));

        PayRequestResult result = service.createPayRequest(retry);

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.request().consentId()).isEqualTo("CONS-001");
        verify(repositoryPort, never()).save(any());
        verify(eventPublisher, never()).publish(any(), anyList(), any());
    }

    @Test
    void reusingKeyWithDifferentPayloadIsAConflict() {
        when(idempotencyPort.reserve(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new IdempotencyRecord("CONS-001", commandWithKey("500.00", "idem-1").fingerprint())));

        assertThatThrownBy(() -> service.createPayRequest(commandWithKey("750.00", "idem-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        verify(repositoryPort, never()).save(any());
        verify(eventPublisher, never()).publish(any(), anyList(), any());
    }

    private static CreatePayRequestCommand commandWithKey(String amount, String key) {
        return new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co", new BigDecimal(amount), "AED",
                Instant.parse("2026-02-10T10:00:00Z"), "ix-1", key);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<PayRequestDomainEvent>> eventsCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private static PayRequest baseRequest() {
        return new PayRequest(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null
        );
    }
}