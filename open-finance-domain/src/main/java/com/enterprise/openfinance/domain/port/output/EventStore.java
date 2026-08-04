package com.enterprise.openfinance.domain.port.output;

import com.enterprise.shared.domain.event.DomainEvent;

import java.util.List;

public interface EventStore {
    void saveEvents(String aggregateId, List<DomainEvent> events, long expectedVersion);
    List<DomainEvent> getEvents(String aggregateId);
}
