package com.enterprise.openfinance.requesttopay.infrastructure.security;

public class DPoPValidationException extends RuntimeException {
    public DPoPValidationException(String message) {
        super(message);
    }

    public DPoPValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
