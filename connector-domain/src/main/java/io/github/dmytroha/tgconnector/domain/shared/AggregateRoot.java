package io.github.dmytroha.tgconnector.domain.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for aggregate roots. Collects domain events raised by state changes
 * so the application layer can publish them after the aggregate is persisted.
 */
public abstract class AggregateRoot<ID> {

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public abstract ID id();

    protected void raise(DomainEvent event) {
        pendingEvents.add(event);
    }

    /**
     * Returns and clears events raised since the last call.
     */
    public List<DomainEvent> pullEvents() {
        var events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }
}
