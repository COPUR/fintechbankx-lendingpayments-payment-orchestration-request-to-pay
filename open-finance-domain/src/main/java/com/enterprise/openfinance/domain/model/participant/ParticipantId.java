package com.enterprise.openfinance.domain.model.participant;

import java.util.Objects;
import java.util.UUID;

public record ParticipantId(String value) {
    public ParticipantId {
        Objects.requireNonNull(value, "Participant ID cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Participant ID cannot be blank");
        }
    }

    public static ParticipantId generate() {
        return new ParticipantId("PART-" + UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
