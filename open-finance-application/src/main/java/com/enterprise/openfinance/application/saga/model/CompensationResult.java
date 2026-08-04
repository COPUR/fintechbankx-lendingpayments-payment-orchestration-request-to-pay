package com.enterprise.openfinance.application.saga.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CompensationResult {
    private SagaId sagaId;
    private int totalCompensations;
    private int successfulCompensations;
    private List<CompensationFailure> failedCompensations;
    private Instant startedAt;
}
