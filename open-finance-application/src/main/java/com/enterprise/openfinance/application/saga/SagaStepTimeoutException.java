package com.enterprise.openfinance.application.saga;

import java.time.Duration;

public class SagaStepTimeoutException extends RuntimeException {
    private final String stepName;
    private final Duration timeout;

    public SagaStepTimeoutException(String message, String stepName, Duration timeout) {
        super(message);
        this.stepName = stepName;
        this.timeout = timeout;
    }

    public String getStepName() {
        return stepName;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
