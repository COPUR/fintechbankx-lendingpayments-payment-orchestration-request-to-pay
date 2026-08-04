package com.enterprise.openfinance.application.saga;

public class SagaStartException extends RuntimeException {
    public SagaStartException(String message, Throwable cause) {
        super(message, cause);
    }
}
