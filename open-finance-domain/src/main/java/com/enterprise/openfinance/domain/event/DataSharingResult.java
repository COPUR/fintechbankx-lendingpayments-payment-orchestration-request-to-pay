package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class DataSharingResult {
    private String sagaId;
    private String requestId;
    private ConsentId consentId;
    private CustomerId customerId;
    private ParticipantId participantId;
    private DataSharingStatus status;
    private String failureReason;
    private Instant failedAt;
    private Instant sharedAt;
    private List<String> dataSources;
    private long dataSize;
    private String encryptionMethod;
    private String deliveryMethod;
    private long executionTimeMs;
}
