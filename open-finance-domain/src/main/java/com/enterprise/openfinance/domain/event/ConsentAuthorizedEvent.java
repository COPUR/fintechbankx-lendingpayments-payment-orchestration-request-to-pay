package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.consent.ConsentId;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.CustomerId;
import com.enterprise.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ConsentAuthorizedEvent implements DomainEvent {
    private final ConsentId consentId;
    private final CustomerId customerId;
    private final ParticipantId participantId;
    private final LocalDateTime authorizedAt;

    public ConsentAuthorizedEvent(ConsentId consentId, CustomerId customerId, ParticipantId participantId, LocalDateTime authorizedAt) {
        this.consentId = consentId;
        this.customerId = customerId;
        this.participantId = participantId;
        this.authorizedAt = authorizedAt;
    }

    @Override
    public String getAggregateId() {
        return consentId.getValue();
    }

    @Override
    public String getAggregateType() {
        return "Consent";
    }

    @Override
    public Map<String, Object> getData() {
        return Map.of(
            "consentId", consentId.getValue(),
            "customerId", customerId.value(),
            "participantId", participantId.value(),
            "authorizedAt", authorizedAt
        );
    }
}
