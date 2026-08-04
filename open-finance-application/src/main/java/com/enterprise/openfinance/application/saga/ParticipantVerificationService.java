package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.event.ParticipantValidationResult;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ParticipantVerificationService {
    CompletableFuture<ParticipantValidationResult> validateParticipant(
            ParticipantId participantId, List<String> certificates, String requestSignature);

    CompletableFuture<Void> revokeValidation(ParticipantId participantId);
}
