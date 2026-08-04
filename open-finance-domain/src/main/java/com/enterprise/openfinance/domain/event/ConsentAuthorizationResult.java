package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ConsentAuthorizationResult {
    private String sagaId;
    private ConsentId consentId;
    private CustomerId customerId;
    private ParticipantId participantId;
    private ConsentAuthorizationStatus status;
    private String failureReason;
    private Instant failedAt;
    private Instant authorizedAt;
    private List<String> completedSteps;
    private long executionTimeMs;
}
