package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

/** Opaque PSU reference of the debtor (customer_id format), as sent by the requesting TPP. */
public record DebtorId(String value) {
    public DebtorId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DebtorId value cannot be blank");
        }
    }
}