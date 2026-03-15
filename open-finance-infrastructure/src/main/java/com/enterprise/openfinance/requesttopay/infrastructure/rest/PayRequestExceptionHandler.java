package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.exception.ResourceNotFoundException;
import com.enterprise.openfinance.requesttopay.infrastructure.rest.dto.PayRequestErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.enterprise.openfinance.requesttopay.infrastructure.rest")
public class PayRequestExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<PayRequestErrorResponse> handleNotFound(ResourceNotFoundException exception,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(PayRequestErrorResponse.of("NOT_FOUND", exception.getMessage(), interactionId(request)));
    }

    @ExceptionHandler(PayRequestFinalizedException.class)
    public ResponseEntity<PayRequestErrorResponse> handleFinalized(PayRequestFinalizedException exception,
                                                                   HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(PayRequestErrorResponse.of("REQUEST_FINALIZED", exception.getMessage(), interactionId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PayRequestErrorResponse> handleInvalidRequest(IllegalArgumentException exception,
                                                                        HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(PayRequestErrorResponse.of("INVALID_REQUEST", exception.getMessage(), interactionId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PayRequestErrorResponse> handleUnexpected(Exception exception,
                                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PayRequestErrorResponse.of("INTERNAL_ERROR", "Unexpected error occurred", interactionId(request)));
    }

    private static String interactionId(HttpServletRequest request) {
        return request.getHeader("X-FAPI-Interaction-ID");
    }
}
