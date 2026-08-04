package com.enterprise.openfinance.application.saga.port.out;

import com.enterprise.openfinance.application.saga.model.CompensationResult;
import com.enterprise.openfinance.application.saga.model.SagaId;
import com.enterprise.openfinance.application.saga.model.StepId;

import java.time.Duration;

/**
 * Port for publishing saga lifecycle events.
 */
public interface SagaEventPublisher {

    void publishSagaStarted(SagaId sagaId, String sagaType);

    void publishStepCompleted(SagaId sagaId, String stepName, StepId stepId);

    void publishStepFailed(SagaId sagaId, String stepName, StepId stepId, Throwable cause);

    void publishSagaCompleted(SagaId sagaId, Duration executionTime);

    void publishSagaAborted(SagaId sagaId, String reason);

    void publishSagaCompensated(SagaId sagaId, CompensationResult result);
}
