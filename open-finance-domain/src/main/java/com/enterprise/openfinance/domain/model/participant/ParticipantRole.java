package com.enterprise.openfinance.domain.model.participant;

public enum ParticipantRole {
    DATA_HOLDER,
    DATA_RECIPIENT,
    PAYMENT_INITIATOR;

    public boolean canShareData() {
        return this == DATA_HOLDER;
    }

    public boolean canReceiveData() {
        return this == DATA_RECIPIENT;
    }

    public boolean canInitiatePayments() {
        return this == PAYMENT_INITIATOR;
    }
}
