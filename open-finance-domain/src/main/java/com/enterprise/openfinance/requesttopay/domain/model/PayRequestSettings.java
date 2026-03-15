package com.enterprise.openfinance.requesttopay.domain.model;

import java.time.Duration;

public record PayRequestSettings(Duration cacheTtl) {

    public PayRequestSettings {
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }
    }
}
