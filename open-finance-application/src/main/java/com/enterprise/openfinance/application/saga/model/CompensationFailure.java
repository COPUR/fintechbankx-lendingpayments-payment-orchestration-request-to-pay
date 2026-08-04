package com.enterprise.openfinance.application.saga.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CompensationFailure {
    private String stepName;
    private String errorMessage;
    private Instant failedAt;
}
