package com.enterprise.openfinance.requesttopay.domain.exception;

/**
 * The TPP reused an x-idempotency-key with a different request payload.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
