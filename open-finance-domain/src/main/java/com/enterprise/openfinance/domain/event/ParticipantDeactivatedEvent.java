package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ParticipantDeactivatedEvent implements DomainEvent {
    private final ParticipantId participantId;
    private final String legalName;
    private final String reason;
    private final LocalDateTime deactivatedAt;

    public ParticipantDeactivatedEvent(ParticipantId participantId, String legalName, String reason, LocalDateTime deactivatedAt) {
        this.participantId = participantId;
        this.legalName = legalName;
        this.reason = reason;
        this.deactivatedAt = deactivatedAt;
    }

    @Override
    public String getAggregateId() {
        return participantId.value();
    }

    @Override
    public String getAggregateType() {
        return "Participant";
    }

    @Override
    public Map<String, Object> getData() {
        return Map.of(
            "participantId", participantId.value(),
            "legalName", legalName,
            "reason", reason,
            "deactivatedAt", deactivatedAt
        );
    }
}
