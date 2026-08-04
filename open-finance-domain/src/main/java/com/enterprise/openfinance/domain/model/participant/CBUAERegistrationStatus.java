package com.enterprise.openfinance.domain.model.participant;

public enum CBUAERegistrationStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED;

    public boolean canBeReactivated() {
        return this == SUSPENDED;
    }
}
