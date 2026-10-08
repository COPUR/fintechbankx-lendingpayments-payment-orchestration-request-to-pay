package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.exception.IdempotencyKeyConflictException;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestAccessDeniedException;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.exception.ResourceNotFoundException;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestErrorResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PayRequestExceptionHandlerTest {

    private final PayRequestExceptionHandler handler = new PayRequestExceptionHandler();

    @Test
    void shouldMapNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FAPI-Interaction-ID", "ix-request-to-pay-err-1");

        ResponseEntity<PayRequestErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("missing"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldMapFinalizedConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FAPI-Interaction-ID", "ix-request-to-pay-err-2");

        ResponseEntity<PayRequestErrorResponse> response = handler.handleFinalized(
                new PayRequestFinalizedException("finalized"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("REQUEST_FINALIZED");
    }

    @Test
    void shouldMapInvalidRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FAPI-Interaction-ID", "ix-request-to-pay-err-3");

        ResponseEntity<PayRequestErrorResponse> response = handler.handleInvalidRequest(
                new IllegalArgumentException("bad input"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void shouldMapIllegalStateToInternalErrorWithAFixedMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FAPI-Interaction-ID", "ix-request-to-pay-err-5");

        ResponseEntity<PayRequestErrorResponse> response = handler.handleIllegalState(
                new IllegalStateException("Idempotency key neither reserved nor found"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Unexpected error occurred");
    }

    @Test
    void shouldMapUnexpectedError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FAPI-Interaction-ID", "ix-request-to-pay-err-4");

        ResponseEntity<PayRequestErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("boom"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void shouldMapOwnershipAndClientMismatchToForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(handler.handlePayRequestNotAccessible(
                new PayRequestAccessDeniedException(PayRequestAccessDeniedException.Reason.OTHER_TPP), request)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleForbidden(new AccessDeniedException("header"), request).getBody().code())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void unknownAndAnotherTppsPayRequestGetTheSameBody() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-FAPI-Interaction-ID", "ix-probe");

        ResponseEntity<PayRequestErrorResponse> unknown = handler.handlePayRequestNotAccessible(
                new PayRequestAccessDeniedException(PayRequestAccessDeniedException.Reason.NOT_FOUND), request);
        ResponseEntity<PayRequestErrorResponse> notOwned = handler.handlePayRequestNotAccessible(
                new PayRequestAccessDeniedException(PayRequestAccessDeniedException.Reason.OTHER_TPP), request);

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN).isEqualTo(notOwned.getStatusCode());
        assertThat(unknown.getBody().code()).isEqualTo("FORBIDDEN").isEqualTo(notOwned.getBody().code());
        assertThat(unknown.getBody().message()).isEqualTo("Pay request not found or not authorised")
                .isEqualTo(notOwned.getBody().message());
    }

    @Test
    void shouldMapIdempotencyAndConcurrencyConflictsTo409() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<PayRequestErrorResponse> reused = handler.handleIdempotencyConflict(
                new IdempotencyKeyConflictException("reused"), request);
        ResponseEntity<PayRequestErrorResponse> concurrent = handler.handleConcurrentUpdate(
                new ObjectOptimisticLockingFailureException("PayRequestJpaEntity", "CONS-1"), request);

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reused.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(concurrent.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(concurrent.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
    }

    @Test
    void shouldMapMalformedRequestTo400WithoutEchoingInput() {
        ResponseEntity<PayRequestErrorResponse> response = handler.handleMalformedRequest(
                new IllegalStateException("parse error at secret-field"), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).doesNotContain("secret-field");
    }
}
