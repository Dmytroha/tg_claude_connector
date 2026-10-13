package io.github.dmytroha.tgconnector.domain.shared;

import java.time.Instant;

/**
 * Marker for something meaningful that happened in the domain.
 */
public interface DomainEvent {

    Instant occurredAt();
}
