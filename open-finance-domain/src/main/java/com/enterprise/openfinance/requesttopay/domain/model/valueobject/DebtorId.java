package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

public record DebtorId(String value) {
    public DebtorId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DebtorId value cannot be blank");
        }
    }
}