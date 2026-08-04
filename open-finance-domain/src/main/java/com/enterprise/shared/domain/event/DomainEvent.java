package com.enterprise.shared.domain.event;

import java.util.Map;

public interface DomainEvent {
    String getAggregateId();
    String getAggregateType();
    Map<String, Object> getData();
}
