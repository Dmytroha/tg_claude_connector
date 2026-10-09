package io.github.dmytroha.tgconnector.domain.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for aggregate roots. Collects domain events raised by state changes
 * so the application layer can publish them after the aggregate is persisted.
 */
public abstract class AggregateRoot<ID> {

    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private long version;

    public abstract ID id();

    /**
     * Optimistic-locking version: {@code 0} means the aggregate has never been persisted.
     */
    public long version() {
        return version;
    }

    /**
     * Called by repositories after a successful write (or when rehydrating) to record the stored version.
     */
    public void markPersisted(long version) {
        this.version = version;
    }

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
