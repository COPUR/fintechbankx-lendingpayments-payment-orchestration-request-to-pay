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
public class ConsentExpiredEvent implements DomainEvent {
    private final ConsentId consentId;
    private final CustomerId customerId;
    private final ParticipantId participantId;
    private final LocalDateTime expiredAt;

    public ConsentExpiredEvent(ConsentId consentId, CustomerId customerId, ParticipantId participantId, LocalDateTime expiredAt) {
        this.consentId = consentId;
        this.customerId = customerId;
        this.participantId = participantId;
        this.expiredAt = expiredAt;
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
            "expiredAt", expiredAt
        );
    }
}
