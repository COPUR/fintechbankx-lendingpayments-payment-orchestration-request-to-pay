package com.enterprise.openfinance.domain.port.output;

import com.enterprise.openfinance.domain.service.DistributedConsentService;
import com.enterprise.shared.domain.event.DomainEvent;

import java.util.List;

public interface DomainEventPublisher {
    void publishAll(List<DomainEvent> events);
    void publishAllWithPriority(List<DomainEvent> events, DistributedConsentService.EventPriority priority);
}
