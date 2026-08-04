package com.enterprise.openfinance.domain.port.output;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentPurpose;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;

import java.util.Set;

public interface ConsentMetricsCollector {
    void recordConsentCreation(ParticipantId participantId, Set<ConsentScope> scopes, ConsentPurpose purpose);
    void recordConsentAuthorization(ParticipantId participantId, Set<ConsentScope> scopes);
    void recordConsentRevocation(ParticipantId participantId, String revocationReason);
    void recordConsentUsage(ParticipantId participantId, String dataType, long dataSize);
    void recordConsentUsageFailure(ConsentId consentId, String dataType, String errorMessage);
    void recordConsentCleanup(int processedCount, int totalExpired);
}
