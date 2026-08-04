package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface NotificationService {
    CompletableFuture<Void> sendConsentAuthorizationRequest(
            CustomerId customerId, ConsentId consentId, ParticipantId participantId, Set<ConsentScope> scopes, String authorizationUrl, Duration timeout);

    CompletableFuture<Void> cancelConsentNotification(CustomerId customerId, ConsentId consentId);

    CompletableFuture<Void> notifyConsentActivated(CustomerId customerId, ConsentId consentId, ParticipantId participantId);

    CompletableFuture<Void> notifyParticipantConsentReady(ParticipantId participantId, ConsentId consentId, Set<ConsentScope> scopes);
}
