package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.event.ConsentValidationResult;
import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ConsentValidationService {
    CompletableFuture<ConsentValidationResult> validateConsentForDataAccess(
            ConsentId consentId, ParticipantId participantId, Set<ConsentScope> requestedScopes, CustomerId customerId, Instant now);

    CompletableFuture<Void> updateConsentUsage(
            ConsentId consentId, Set<ConsentScope> requestedScopes, long dataSize, Instant now);

    CompletableFuture<ConsentValidationResult> verifyConsentRequest(
            CustomerId customerId, ParticipantId participantId, Set<ConsentScope> scopes, String purpose, Instant expirationDate);

    CompletableFuture<ConsentId> createPendingConsent(
            ConsentId consentId, CustomerId customerId, ParticipantId participantId, Set<ConsentScope> scopes, String purpose, Instant expirationDate);

    CompletableFuture<Void> deleteConsent(ConsentId consentId);

    CompletableFuture<Void> activateConsent(ConsentId consentId, Instant now, Instant expirationDate);
}
