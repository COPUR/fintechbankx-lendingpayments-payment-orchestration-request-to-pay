package com.enterprise.openfinance.domain.model.consent;

import com.enterprise.openfinance.domain.event.ConsentAuthorizedEvent;
import com.enterprise.openfinance.domain.event.ConsentCreatedEvent;
import com.enterprise.openfinance.domain.event.ConsentExpiredEvent;
import com.enterprise.openfinance.domain.event.ConsentRevokedEvent;
import com.enterprise.openfinance.domain.model.participant.ParticipantId;
import com.enterprise.shared.domain.AggregateRoot;
import com.enterprise.shared.domain.CustomerId;
import com.enterprise.shared.domain.event.DomainEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Consent aggregate root representing customer consent for data sharing in Open Finance.
 */
@Getter
public class Consent extends AggregateRoot<ConsentId> {

    private ConsentId id;
    private CustomerId customerId;
    private ParticipantId participantId;
    private Set<ConsentScope> scopes;
    private ConsentPurpose purpose;
    private LocalDateTime createdAt;
    
    private ConsentStatus status;
    private LocalDateTime expiryDate;
    private LocalDateTime authorizedAt;
    private LocalDateTime revokedAt;
    private String revocationReason;
    private LocalDateTime renewedAt;
    private long version = 0L;
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    public Consent() {
        // Empty constructor for event sourcing
    }

    @Builder
    public Consent(
            ConsentId id,
            CustomerId customerId,
            ParticipantId participantId,
            Set<ConsentScope> scopes,
            ConsentPurpose purpose,
            LocalDateTime expiryDate) {
        
        validateRequiredFields(id, customerId, participantId, scopes, purpose, expiryDate);
        
        this.id = id;
        this.customerId = customerId;
        this.participantId = participantId;
        this.scopes = Set.copyOf(scopes);
        this.purpose = purpose;
        this.expiryDate = expiryDate;
        this.createdAt = LocalDateTime.now();
        this.status = ConsentStatus.PENDING;
        
        var event = new ConsentCreatedEvent(
                this.id,
                this.customerId,
                this.participantId,
                this.scopes,
                this.purpose,
                this.createdAt
        );
        addUncommittedEvent(event);
        addDomainEvent(event);
    }

    public static Consent create(ConsentId id, CustomerId customerId, ParticipantId participantId, Set<ConsentScope> scopes, ConsentPurpose purpose, Instant expiryDate) {
        return Consent.builder()
                .id(id)
                .customerId(customerId)
                .participantId(participantId)
                .scopes(scopes)
                .purpose(purpose)
                .expiryDate(LocalDateTime.ofInstant(expiryDate, java.time.ZoneId.systemDefault()))
                .build();
    }

    public void authorize(Object authContext) {
        if (status != ConsentStatus.PENDING) {
            throw new IllegalStateException("Cannot authorize consent that is not pending.");
        }
        
        this.status = ConsentStatus.AUTHORIZED;
        this.authorizedAt = LocalDateTime.now();
        
        var event = new ConsentAuthorizedEvent(
                this.id,
                this.customerId,
                this.participantId,
                this.authorizedAt
        );
        addUncommittedEvent(event);
        addDomainEvent(event);
    }

    public void revoke(String reason) {
        if (status == ConsentStatus.REVOKED) {
            throw new IllegalStateException("Consent is already revoked");
        }
        
        this.status = ConsentStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
        this.revocationReason = reason;
        
        var event = new ConsentRevokedEvent(
                this.id,
                this.customerId,
                this.participantId,
                this.revokedAt,
                reason
        );
        addUncommittedEvent(event);
        addDomainEvent(event);
    }

    public void renew(LocalDateTime newExpiryDate) {
        this.expiryDate = newExpiryDate;
        this.renewedAt = LocalDateTime.now();
    }

    public void markAsExpired() {
        if (status == ConsentStatus.AUTHORIZED && isExpired()) {
            this.status = ConsentStatus.EXPIRED;
            
            var event = new ConsentExpiredEvent(
                    this.id,
                    this.customerId,
                    this.participantId,
                    LocalDateTime.now()
            );
            addUncommittedEvent(event);
            addDomainEvent(event);
        }
    }

    public void recordUsage(Object accessContext) {
        // Record usage logic
    }

    public void apply(DomainEvent event) {
        // Apply event logic to reconstruct state
    }

    private void addUncommittedEvent(DomainEvent event) {
        this.uncommittedEvents.add(event);
    }

    public List<DomainEvent> getUncommittedEvents() {
        return uncommittedEvents;
    }

    public long getVersion() {
        return version;
    }

    public boolean isExpired() {
        return expiryDate.isBefore(LocalDateTime.now());
    }

    public boolean isActive() {
        return status == ConsentStatus.AUTHORIZED && !isExpired();
    }

    public boolean hasScope(ConsentScope scope) {
        return scopes.contains(scope);
    }

    public boolean hasAllScopes(Set<ConsentScope> requiredScopes) {
        return scopes.containsAll(requiredScopes);
    }

    private void validateRequiredFields(
            ConsentId id,
            CustomerId customerId,
            ParticipantId participantId,
            Set<ConsentScope> scopes,
            ConsentPurpose purpose,
            LocalDateTime expiryDate) {
        if (id == null || customerId == null || participantId == null || scopes == null || scopes.isEmpty() || purpose == null || expiryDate == null) {
            throw new IllegalArgumentException("Missing required fields");
        }
    }

    @Override
    public ConsentId getId() {
        return id;
    }
}
