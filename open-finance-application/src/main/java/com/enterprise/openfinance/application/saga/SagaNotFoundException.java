package com.enterprise.openfinance.application.saga;

public class SagaNotFoundException extends RuntimeException {
    public SagaNotFoundException(String message) {
        super(message);
    }
}
