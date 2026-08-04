package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentPurpose;
import com.enterprise.openfinance.domain.model.consent.ConsentScope;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Data
@Builder
public class ConsentCreationRequest {
    private CustomerId customerId;
    private ParticipantId participantId;
    private Set<ConsentScope> scopes;
    private ConsentPurpose purpose;
    private Optional<Integer> validityDays;
    private String interactionId;

    public Map<String, Object> toMap() {
        return Map.of(
            "customerId", customerId.value(),
            "participantId", participantId.value()
        );
    }
}
