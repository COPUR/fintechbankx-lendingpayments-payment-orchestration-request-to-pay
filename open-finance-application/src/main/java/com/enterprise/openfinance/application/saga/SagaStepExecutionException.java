package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.application.saga.model.StepId;

public class SagaStepExecutionException extends RuntimeException {
    private final StepId stepId;

    public SagaStepExecutionException(String message, StepId stepId, Throwable cause) {
        super(message, cause);
        this.stepId = stepId;
    }

    public StepId getStepId() {
        return stepId;
    }
}
