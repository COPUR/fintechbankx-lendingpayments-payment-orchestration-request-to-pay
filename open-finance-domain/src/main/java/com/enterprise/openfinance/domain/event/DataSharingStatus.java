package com.enterprise.openfinance.domain.event;

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
