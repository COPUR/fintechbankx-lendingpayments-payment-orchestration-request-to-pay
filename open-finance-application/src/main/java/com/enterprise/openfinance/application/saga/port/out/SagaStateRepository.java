package com.enterprise.openfinance.application.saga.port.out;

import com.enterprise.openfinance.application.saga.model.SagaExecution;
import com.enterprise.openfinance.application.saga.model.SagaId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Port for persisting and loading saga state.
 */
public interface SagaStateRepository {

    CompletableFuture<SagaExecution> saveSagaState(SagaExecution saga);

    CompletableFuture<Optional<SagaExecution>> loadSagaState(SagaId sagaId);

    CompletableFuture<List<SagaExecution>> findInterruptedSagas();
}
