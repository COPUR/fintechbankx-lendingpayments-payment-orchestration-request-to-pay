package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class ConsentAuthorizationRequest {
    private ConsentId consentId;
    private ParticipantId participantId;
    private CustomerId customerId;
    private List<String> certificates;
    private String requestSignature;
    private Set<ConsentScope> scopes;
    private String purpose;
    private Instant expirationDate;
    private String authorizationUrl;
}
