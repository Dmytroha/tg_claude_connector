package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.shared.DomainEvent;

import java.time.Instant;

/**
 * Domain events raised by the {@link Source} aggregate.
 */
public final class SourceEvents {

    private SourceEvents() {
    }

    public record SourceRegistered(SourceId sourceId, ChatReference reference, Instant occurredAt) implements DomainEvent {
    }

    public record SourcePaused(SourceId sourceId, Instant occurredAt) implements DomainEvent {
    }

    public record SourceResumed(SourceId sourceId, Instant occurredAt) implements DomainEvent {
    }
}
