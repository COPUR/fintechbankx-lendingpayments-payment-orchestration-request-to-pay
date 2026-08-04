package com.enterprise.openfinance.domain.event;

import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ParticipantValidatedEvent implements DomainEvent {
    private final ParticipantId participantId;
    private final boolean isValid;
    private final String validationDetails;
    private final Instant occurredAt;
    private final String correlationId;
    private final String causationId;
    private final Long version;

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
            "isValid", isValid,
            "validationDetails", validationDetails != null ? validationDetails : "none",
            "validatedAt", occurredAt != null ? occurredAt : Instant.now()
        );
    }

    public static ParticipantValidatedEventBuilder builder() {
        return new ParticipantValidatedEventBuilder()
            .correlationId(UUID.randomUUID().toString())
            .causationId(UUID.randomUUID().toString())
            .version(1L);
    }
}
