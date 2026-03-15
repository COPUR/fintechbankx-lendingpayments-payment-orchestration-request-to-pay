package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.exception.ResourceNotFoundException;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestErrorResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
}
