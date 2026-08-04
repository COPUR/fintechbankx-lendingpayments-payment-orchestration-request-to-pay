package com.enterprise.openfinance.application.saga;

import com.enterprise.openfinance.domain.event.EncryptedData;
import com.enterprise.openfinance.domain.event.TransformedData;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;

import java.util.concurrent.CompletableFuture;

public interface DataEncryptionService {
    CompletableFuture<EncryptedData> encryptForParticipant(
            TransformedData transformedData, ParticipantId participantId, String encryptionMethod, String participantPublicKey);

    CompletableFuture<Void> securelyDeleteEncryptedData(String encryptionId);
}
