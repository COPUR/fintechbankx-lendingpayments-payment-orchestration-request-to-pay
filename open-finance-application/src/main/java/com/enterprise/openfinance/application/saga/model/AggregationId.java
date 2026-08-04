package com.enterprise.openfinance.application.saga.model;

import java.util.Objects;
import java.util.UUID;

public record AggregationId(String value) {
    public AggregationId {
        Objects.requireNonNull(value, "Aggregation ID cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Aggregation ID cannot be blank");
        }
    }

    public static AggregationId generate() {
        return new AggregationId("AGGR-" + UUID.randomUUID().toString());
    }

    public static AggregationId of(String value) {
        return new AggregationId(value);
    }

    public String getValue() {
        return value();
    }

    @Override
    public String toString() {
        return value;
    }
}
