package com.enterprise.openfinance.application.saga;

public class SagaStepFailedException extends RuntimeException {
    private final String stepName;
    private final String errorCode;

    public SagaStepFailedException(String message, String stepName, String errorCode) {
        super(message);
        this.stepName = stepName;
        this.errorCode = errorCode;
    }

    public SagaStepFailedException(String message, String stepName, Throwable cause) {
        super(message, cause);
        this.stepName = stepName;
        this.errorCode = "FAILED";
    }

    public String getStepName() {
        return stepName;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
