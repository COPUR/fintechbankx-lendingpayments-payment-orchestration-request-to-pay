package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ComplianceService {
    CompletableFuture<Void> recordComplianceEvent(
            String eventType, ConsentId consentId, ParticipantId participantId, Map<String, Object> metadata);
}
