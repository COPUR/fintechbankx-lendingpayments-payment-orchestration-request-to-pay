package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.application.saga.model.SagaId;
import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface AuditService {
    CompletableFuture<Void> recordConsentAuthorized(
            ConsentId consentId, CustomerId customerId, ParticipantId participantId, SagaId sagaId);

    CompletableFuture<Void> recordSagaCompensationFailed(
            SagaId sagaId, ConsentId consentId, Throwable compensationError);

    CompletableFuture<Void> recordSagaCompensationCompleted(
            SagaId sagaId, ConsentId consentId);

    CompletableFuture<Void> recordDataAccess(
            ConsentId consentId, CustomerId customerId, ParticipantId participantId, Set<ConsentScope> requestedScopes, List<String> dataSources, Instant deliveryTimestamp, SagaId sagaId);
}
