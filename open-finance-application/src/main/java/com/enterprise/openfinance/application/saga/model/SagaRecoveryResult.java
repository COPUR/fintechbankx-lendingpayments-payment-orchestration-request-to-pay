package com.enterprise.openfinance.application.saga.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class SagaRecoveryResult {
    private int totalSagas;
    private int successfulRecoveries;
    private int failedRecoveries;
    private List<SagaRecovery> recoveredSagas;
    private Instant recoveryCompletedAt;

    @Data
    @Builder
    public static class SagaRecovery {
        private SagaId sagaId;
        private boolean success;
        private String status;
        private String message;

        public static SagaRecovery compensated(SagaId sagaId) {
            return SagaRecovery.builder().sagaId(sagaId).success(true).status("COMPENSATED").build();
        }

        public static SagaRecovery alreadyCompleted(SagaId sagaId) {
            return SagaRecovery.builder().sagaId(sagaId).success(true).status("ALREADY_COMPLETED").build();
        }

        public static SagaRecovery alreadyCompensated(SagaId sagaId) {
            return SagaRecovery.builder().sagaId(sagaId).success(true).status("ALREADY_COMPENSATED").build();
        }

        public static SagaRecovery failed(SagaId sagaId, String message) {
            return SagaRecovery.builder().sagaId(sagaId).success(false).status("FAILED").message(message).build();
        }
    }
}
