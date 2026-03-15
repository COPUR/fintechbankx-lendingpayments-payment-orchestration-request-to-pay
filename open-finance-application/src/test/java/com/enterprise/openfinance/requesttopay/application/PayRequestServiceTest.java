package com.enterprise.openfinance.requesttopay.application;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.exception.ResourceNotFoundException;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestSettings;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestCachePort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestNotificationPort;
import com.enterprise.openfinance.requesttopay.domain.port.out.PayRequestRepositoryPort;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayRequestServiceTest {

    private final PayRequestRepositoryPort repositoryPort = mock(PayRequestRepositoryPort.class);
    private final PayRequestCachePort cachePort = mock(PayRequestCachePort.class);
    private final PayRequestNotificationPort notificationPort = mock(PayRequestNotificationPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-02-10T10:00:00Z"), ZoneOffset.UTC);

    private final PayRequestService service = new PayRequestService(
            repositoryPort,
            cachePort,
            notificationPort,
            new PayRequestSettings(Duration.ofMinutes(2)),
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
        verify(notificationPort).notifyPayRequestCreated(result.request());
    }

    @Test
    void shouldReturnCachedStatusOnHit() {
        PayRequest request = baseRequest();
        when(cachePort.getStatus("pay-request:CONS-001:TPP-001", Instant.parse("2026-02-10T10:00:00Z")))
                .thenReturn(Optional.of(new PayRequestResult(request, true)));

        PayRequestResult result = service.getPayRequestStatus(new GetPayRequestStatusQuery("CONS-001", "TPP-001", "ix"));

        assertThat(result.cacheHit()).isTrue();
        verify(repositoryPort, never()).findById("CONS-001");
    }

    @Test
    void shouldReadAndCacheOnMiss() {
        PayRequest request = baseRequest();
        when(cachePort.getStatus(any(), any())).thenReturn(Optional.empty());
        when(repositoryPort.findById("CONS-001")).thenReturn(Optional.of(request));

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
        when(repositoryPort.findById("CONS-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayRequestStatus(new GetPayRequestStatusQuery("CONS-404", "TPP-001", "ix")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pay request not found");
    }

    @Test
    void shouldRejectDuplicateFinalize() {
        PayRequest request = baseRequest().consume("PAY-001", Instant.parse("2026-02-10T11:00:00Z"));
        when(repositoryPort.findById("CONS-001")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.rejectPayRequest("CONS-001", "TPP-001", "ix"))
                .isInstanceOf(PayRequestFinalizedException.class)
                .hasMessageContaining("finalized");
    }

    @Test
    void shouldRejectWrongOwner() {
        PayRequest request = baseRequest();
        when(repositoryPort.findById("CONS-001")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getPayRequestStatus(new GetPayRequestStatusQuery("CONS-001", "TPP-XYZ", "ix")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participant");
    }

    @Test
    void shouldConsumePayRequest() {
        PayRequest request = baseRequest();
        when(repositoryPort.findById("CONS-001")).thenReturn(Optional.of(request));
        when(repositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PayRequestResult result = service.acceptPayRequest("CONS-001", "TPP-001", "PAY-123", "ix");

        assertThat(result.status()).isEqualTo(PayRequestStatus.CONSUMED);
        ArgumentCaptor<PayRequest> captor = ArgumentCaptor.forClass(PayRequest.class);
        verify(repositoryPort).save(captor.capture());
        assertThat(captor.getValue().paymentIdOptional()).contains("PAY-123");
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
