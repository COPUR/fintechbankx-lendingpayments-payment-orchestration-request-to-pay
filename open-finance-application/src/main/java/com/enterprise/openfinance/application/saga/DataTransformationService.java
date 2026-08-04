package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.event.AggregatedData;
import com.enterprise.openfinance.domain.event.TransformedData;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface DataTransformationService {
    CompletableFuture<TransformedData> transformForParticipant(
            AggregatedData aggregatedData, ParticipantId participantId, Set<ConsentScope> requestedScopes, String dataFormat, String complianceRequirements);

    CompletableFuture<Void> cleanupTransformedData(String transformationId);
}
