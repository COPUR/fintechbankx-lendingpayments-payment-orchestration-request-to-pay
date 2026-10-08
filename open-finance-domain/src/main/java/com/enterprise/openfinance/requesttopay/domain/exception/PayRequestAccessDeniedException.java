package com.enterprise.openfinance.requesttopay.domain.exception;

/**
 * The calling TPP is not the TPP that created the pay request.
 */
public class PayRequestAccessDeniedException extends RuntimeException {

    public PayRequestAccessDeniedException(String message) {
        super(message);
    }
}
