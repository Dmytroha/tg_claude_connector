package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.shared.DomainEvent;

import java.time.Instant;

/**
 * Raised when a new Telegram message was accepted by the connector.
 * This is the main integration event downstream consumers are interested in.
 */
public record MessageReceived(MessageKey key, Instant postedAt, Instant occurredAt) implements DomainEvent {
}
