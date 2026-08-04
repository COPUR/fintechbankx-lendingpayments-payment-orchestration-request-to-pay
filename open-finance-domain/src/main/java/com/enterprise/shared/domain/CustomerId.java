package com.enterprise.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(String value) {
    public CustomerId {
        Objects.requireNonNull(value, "Customer ID cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be blank");
        }
    }

    public static CustomerId generate() {
        return new CustomerId("CUST-" + UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
