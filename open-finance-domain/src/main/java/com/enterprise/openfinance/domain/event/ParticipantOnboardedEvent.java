package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.openfinance.domain.model.participant.ParticipantRole;
import com.enterprise.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ParticipantOnboardedEvent implements DomainEvent {
    private final ParticipantId participantId;
    private final String legalName;
    private final ParticipantRole role;
    private final LocalDateTime occurredAt;
    private final String correlationId;
    private final String causationId;
    private final Long version;

    public ParticipantOnboardedEvent(ParticipantId participantId, String legalName, ParticipantRole role, LocalDateTime occurredAt) {
        this.participantId = participantId;
        this.legalName = legalName;
        this.role = role;
        this.occurredAt = occurredAt;
        this.correlationId = UUID.randomUUID().toString();
        this.causationId = UUID.randomUUID().toString();
        this.version = 1L;
    }

    public ParticipantOnboardedEvent(ParticipantId participantId, String legalName, ParticipantRole role, LocalDateTime occurredAt, String correlationId, String causationId, Long version) {
        this.participantId = participantId;
        this.legalName = legalName;
        this.role = role;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.version = version;
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
            "role", role,
            "onboardedAt", occurredAt
        );
    }
}