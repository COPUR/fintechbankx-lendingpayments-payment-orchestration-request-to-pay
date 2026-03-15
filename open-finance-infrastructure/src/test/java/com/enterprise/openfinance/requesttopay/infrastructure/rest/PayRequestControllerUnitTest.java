package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.domain.query.GetPayRequestStatusQuery;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestDecisionRequest;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestRequest;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestResponse;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestStatusResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@Tag("unit")
class PayRequestControllerUnitTest {

    @Test
    void shouldCreateAndReturnPayRequest() {
        PayRequestUseCase useCase = Mockito.mock(PayRequestUseCase.class);
        PayRequestController controller = new PayRequestController(useCase);

        PayRequest request = sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null);
        Mockito.when(useCase.createPayRequest(Mockito.any()))
                .thenReturn(new PayRequestResult(request, false));
        Mockito.when(useCase.getPayRequestStatus(Mockito.any()))
                .thenReturn(new PayRequestResult(request, false));

        ResponseEntity<PayRequestResponse> created = controller.createPayRequest(
                "DPoP token",
                "proof",
                "ix-request-to-pay-1",
                "TPP-001",
                new PayRequestRequest(new PayRequestRequest.Data(
                        "PSU-001",
                        "Utilities Co",
                        new PayRequestRequest.Amount("500.00", "AED")
                ))
        );

        ResponseEntity<PayRequestStatusResponse> status = controller.getPayRequestStatus(
                "DPoP token",
                "proof",
                "ix-request-to-pay-1",
                "TPP-001",
                "CONS-001",
                null
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getFirst("X-OF-Cache")).isEqualTo("MISS");
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturnNotModifiedWhenEtagMatches() {
        PayRequestUseCase useCase = Mockito.mock(PayRequestUseCase.class);
        PayRequestController controller = new PayRequestController(useCase);

        PayRequest request = sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null);
        Mockito.when(useCase.getPayRequestStatus(Mockito.any()))
                .thenReturn(new PayRequestResult(request, false));

        ResponseEntity<PayRequestStatusResponse> first = controller.getPayRequestStatus(
                "DPoP token",
                "proof",
                "ix-request-to-pay-1",
                "TPP-001",
                "CONS-001",
                null
        );

        ResponseEntity<PayRequestStatusResponse> second = controller.getPayRequestStatus(
                "DPoP token",
                "proof",
                "ix-request-to-pay-1",
                "TPP-001",
                "CONS-001",
                first.getHeaders().getETag()
        );

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void shouldAcceptAndRejectPayRequest() {
        PayRequestUseCase useCase = Mockito.mock(PayRequestUseCase.class);
        PayRequestController controller = new PayRequestController(useCase);

        PayRequest consumed = sampleRequest(PayRequestStatus.CONSUMED, "PAY-123");
        Mockito.when(useCase.acceptPayRequest("CONS-001", "TPP-001", "PAY-123", "ix-request-to-pay-2"))
                .thenReturn(new PayRequestResult(consumed, false));

        ResponseEntity<PayRequestStatusResponse> accept = controller.acceptPayRequest(
                "DPoP token",
                "proof",
                "ix-request-to-pay-2",
                "TPP-001",
                "CONS-001",
                new PayRequestDecisionRequest("PAY-123", null)
        );

        assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accept.getBody()).isNotNull();
        assertThat(accept.getBody().data().status()).isEqualTo("Consumed");

        PayRequest rejected = sampleRequest(PayRequestStatus.REJECTED, null);
        Mockito.when(useCase.rejectPayRequest("CONS-001", "TPP-001", "ix-request-to-pay-3"))
                .thenReturn(new PayRequestResult(rejected, false));

        ResponseEntity<PayRequestStatusResponse> reject = controller.rejectPayRequest(
                "DPoP token",
                "proof",
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
    void shouldUseUnknownTppWhenFinancialIdMissing() {
        PayRequestUseCase useCase = Mockito.mock(PayRequestUseCase.class);
        PayRequestController controller = new PayRequestController(useCase);

        PayRequest request = sampleRequest(PayRequestStatus.AWAITING_AUTHORISATION, null);
        Mockito.when(useCase.getPayRequestStatus(Mockito.any()))
                .thenReturn(new PayRequestResult(request, false));

        controller.getPayRequestStatus(
                "DPoP token",
                "proof",
                "ix-request-to-pay-4",
                null,
                "CONS-001",
                null
        );

        ArgumentCaptor<GetPayRequestStatusQuery> captor = ArgumentCaptor.forClass(GetPayRequestStatusQuery.class);
        verify(useCase).getPayRequestStatus(captor.capture());
        assertThat(captor.getValue().tppId()).isEqualTo("UNKNOWN_TPP");
    }

    @Test
    void shouldRejectInvalidAuthorization() {
        PayRequestUseCase useCase = Mockito.mock(PayRequestUseCase.class);
        PayRequestController controller = new PayRequestController(useCase);

        assertThatThrownBy(() -> controller.createPayRequest(
                "Basic invalid",
                "proof",
                "ix-request-to-pay-1",
                "TPP-001",
                new PayRequestRequest(new PayRequestRequest.Data(
                        "PSU-001",
                        "Utilities Co",
                        new PayRequestRequest.Amount("500.00", "AED")
                ))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bearer or DPoP");
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
