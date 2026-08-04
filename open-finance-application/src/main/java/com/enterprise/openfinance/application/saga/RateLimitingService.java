package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.event.RateLimitResult;
import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface RateLimitingService {
    CompletableFuture<RateLimitResult> checkRateLimit(
            ParticipantId participantId, ConsentId consentId, Set<ConsentScope> requestedScopes, long dataSize);

    CompletableFuture<Void> restoreQuota(
            ParticipantId participantId, Set<ConsentScope> requestedScopes, long dataSize);
}
