package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.application.saga.model.AggregationId;
import com.enterprise.openfinance.domain.event.DataDeliveryResult;
import com.enterprise.openfinance.domain.event.EncryptedData;
import com.enterprise.openfinance.domain.event.PlatformData;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface DataAggregationService {
    CompletableFuture<PlatformData> aggregateLoanData(
            CustomerId customerId, Set<ConsentScope> requestedScopes);

    CompletableFuture<PlatformData> aggregateAmanahFiData(
            CustomerId customerId, Set<ConsentScope> requestedScopes);

    CompletableFuture<PlatformData> aggregateMasrufiData(
            CustomerId customerId, Set<ConsentScope> requestedScopes);

    CompletableFuture<PlatformData> aggregateExternalData(
            CustomerId customerId, Set<ConsentScope> requestedScopes, ParticipantId participantId);

    CompletableFuture<Void> cleanupAggregatedData(AggregationId aggregationId);

    CompletableFuture<DataDeliveryResult> deliverDataToParticipant(
            EncryptedData encryptedData, ParticipantId participantId, String deliveryEndpoint, String deliveryMethod, String callbackUrl);
}
