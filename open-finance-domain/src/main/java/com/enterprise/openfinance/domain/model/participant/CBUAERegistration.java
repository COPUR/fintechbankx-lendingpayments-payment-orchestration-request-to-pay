package com.enterprise.openfinance.domain.model.participant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CBUAERegistration {
    private final String licenseNumber;
    private final CBUAERegistrationStatus status;

    public CBUAERegistration suspend(String reason) {
        return new CBUAERegistration(this.licenseNumber, CBUAERegistrationStatus.SUSPENDED);
    }

    public CBUAERegistration activate() {
        return new CBUAERegistration(this.licenseNumber, CBUAERegistrationStatus.ACTIVE);
    }

    public boolean isExpiringSoon(int days) {
        return false; // Stub
    }
}
