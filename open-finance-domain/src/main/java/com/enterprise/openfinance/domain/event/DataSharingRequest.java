package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class DataSharingRequest {
    private String requestId;
    private ConsentId consentId;
    private ParticipantId participantId;
    private CustomerId customerId;
    private Set<ConsentScope> requestedScopes;
    private long dataSize;
    private String dataFormat;
    private String complianceRequirements;
    private String encryptionMethod;
    private String participantPublicKey;
    private String deliveryEndpoint;
    private String deliveryMethod;
    private String callbackUrl;
}
