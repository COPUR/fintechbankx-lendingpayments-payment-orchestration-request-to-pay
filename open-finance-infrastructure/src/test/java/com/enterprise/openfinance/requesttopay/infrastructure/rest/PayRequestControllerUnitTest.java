package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestDecisionRequest;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestRequest;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestResponse;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@Tag("unit")
class PayRequestControllerUnitTest {

    private final PayRequestUseCase useCase = Mockito.mock(PayRequestUseCase.class);
    private final PayRequestController controller = new PayRequestController(useCase);

    @BeforeEach
    void authenticateAsTpp001() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "PS256").claim("azp", "TPP-001").build()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateAndReturnPayRequest() {
        PayRequest request = sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null);
        Mockito.when(useCase.createPayRequest(Mockito.any()))
                .thenReturn(new PayRequestResult(request, false));

        ResponseEntity<PayRequestResponse> created = controller.createPayRequest(
                "ix-request-to-pay-1",
                "idemp-key-1",
                "TPP-001",
                new PayRequestRequest(new PayRequestRequest.Data(
                        "PSU-001",
                        "Utilities Co",
                        new PayRequestRequest.Amount("500.00", "AED")
                ))
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getFirst("X-OF-Cache")).isEqualTo("MISS");
    }

    @Test
    void shouldReturnNotModifiedWhenEtagMatches() {
        PayRequest request = sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null);
        Mockito.when(useCase.getPayRequestStatus(Mockito.any()))
                .thenReturn(new PayRequestResult(request, false));

        ResponseEntity<PayRequestStatusResponse> first = controller.getPayRequestStatus(
                "ix-request-to-pay-1",
                "TPP-001",
                "CONS-001",
                null
        );

        ResponseEntity<PayRequestStatusResponse> second = controller.getPayRequestStatus(
                "ix-request-to-pay-1",
                "TPP-001",
                "CONS-001",
                first.getHeaders().getETag()
        );

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void shouldAcceptAndRejectPayRequest() {
        PayRequest consumed = sampleRequest(PayRequestStatus.CONSUMED, "PAY-123");
        Mockito.when(useCase.acceptPayRequest("CONS-001", "TPP-001", "PAY-123", null, "ix-request-to-pay-2"))
                .thenReturn(new PayRequestResult(consumed, false));

        ResponseEntity<PayRequestStatusResponse> accept = controller.acceptPayRequest(
                "ix-request-to-pay-2",
                "TPP-001",
                "CONS-001",
                new PayRequestDecisionRequest("PAY-123", null)
        );

        assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accept.getBody()).isNotNull();
        assertThat(accept.getBody().data().status()).isEqualTo("Consumed");

        PayRequest rejected = sampleRequest(PayRequestStatus.REJECTED, null);
        Mockito.when(useCase.rejectPayRequest("CONS-001", "TPP-001", "User rejected", "ix-request-to-pay-3"))
                .thenReturn(new PayRequestResult(rejected, false));

        ResponseEntity<PayRequestStatusResponse> reject = controller.rejectPayRequest(
                "ix-request-to-pay-3",
                "TPP-001",
                "CONS-001",
                new PayRequestDecisionRequest(null, "User rejected")
        );

        assertThat(reject.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reject.getBody()).isNotNull();
        assertThat(reject.getBody().data().status()).isEqualTo("Rejected");
    }

    @Test
    void tppComesFromTheTokenWhenFinancialIdIsMissing() {
        PayRequest request = sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null);
        Mockito.when(useCase.getPayRequestStatus(Mockito.any()))
                .thenReturn(new PayRequestResult(request, false));

        controller.getPayRequestStatus(
                "ix-request-to-pay-4",
                null,
                "CONS-001",
                null
        );

        ArgumentCaptor<GetPayRequestStatusQuery> captor = ArgumentCaptor.forClass(GetPayRequestStatusQuery.class);
        verify(useCase).getPayRequestStatus(captor.capture());
        assertThat(captor.getValue().tppId()).isEqualTo("TPP-001");
    }

    @Test
    void withoutAnAccessTokenTheCallIsRefusedEvenWithAFinancialId() {
        SecurityContextHolder.clearContext();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.getPayRequestStatus(
                        "ix-request-to-pay-5", "TPP-001", "CONS-001", null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        Mockito.verifyNoInteractions(useCase);
    }

    @Test
    void createPassesTheIdempotencyKeyAndFlagsAReplay() {
        PayRequest request = sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null);
        Mockito.when(useCase.createPayRequest(Mockito.any())).thenReturn(PayRequestResult.replayOf(request));

        ResponseEntity<PayRequestResponse> created = controller.createPayRequest("ix-1", "idem-42", "TPP-001",
                new PayRequestRequest(new PayRequestRequest.Data("PSU-001", "Utilities Co",
                        new PayRequestRequest.Amount("500.00", "AED"))));

        ArgumentCaptor<CreatePayRequestCommand> command = ArgumentCaptor.forClass(CreatePayRequestCommand.class);
        verify(useCase).createPayRequest(command.capture());
        assertThat(command.getValue().idempotencyKey()).isEqualTo("idem-42");
        assertThat(command.getValue().tppId()).isEqualTo("TPP-001");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getFirst("X-Idempotent-Replay")).isEqualTo("true");
        assertThat(created.getBody().data().consentId()).isEqualTo("CONS-001");
    }

    @Test
    void malformedAmountIsAnInvalidRequest() {
        PayRequestRequest body = new PayRequestRequest(new PayRequestRequest.Data("PSU-001", "Utilities Co",
                new PayRequestRequest.Amount("five hundred", "AED")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> body.toCommand("TPP", "ix", "k"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("decimal");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PayRequestRequest(null).toCommand("TPP", "ix"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("required");
    }

    private static PayRequest sampleRequest(PayRequestStatus status, String paymentId) {
        return new PayRequest(
                "CONS-001",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                status,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                paymentId
        );
    }
}
