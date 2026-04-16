package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

public record CreditorId(String value) {
    public CreditorId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CreditorId value cannot be blank");
        }
    }
}