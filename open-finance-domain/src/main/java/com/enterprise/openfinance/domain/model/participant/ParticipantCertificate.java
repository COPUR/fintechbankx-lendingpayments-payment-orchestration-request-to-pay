package com.enterprise.openfinance.domain.model.participant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParticipantCertificate {
    private final String certificateId;
    private final boolean active;
}
