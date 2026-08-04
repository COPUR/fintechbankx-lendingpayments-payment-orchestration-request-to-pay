package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.event.CBUAERegistrationResult;
import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface CBUAEIntegrationService {
    CompletableFuture<CBUAERegistrationResult> registerAuthorizedConsent(
            ConsentId consentId, ParticipantId participantId, CustomerId customerId, Set<ConsentScope> scopes, Instant authorizedTimestamp);

    CompletableFuture<Void> deregisterConsent(ConsentId consentId);
}
