package com.bank.shared.kernel.domain;

import java.util.ArrayList;
import java.util.List;

public abstract class Entity<T> {
    private final List<Object> domainEvents = new ArrayList<>();

    public abstract T getId();

    protected void addDomainEvent(Object event) {
        domainEvents.add(event);
    }

    public List<Object> getDomainEvents() {
        return domainEvents;
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
