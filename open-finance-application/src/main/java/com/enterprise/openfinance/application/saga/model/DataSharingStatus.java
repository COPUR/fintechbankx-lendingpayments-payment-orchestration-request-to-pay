package com.enterprise.openfinance.application.saga.model;

public enum DataSharingStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMITED,
    TIMEOUT
}
