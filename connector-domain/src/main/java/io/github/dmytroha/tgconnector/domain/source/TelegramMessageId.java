package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.shared.DomainException;

/**
 * Message id as assigned by Telegram. Unique and monotonically increasing within one chat.
 */
public record TelegramMessageId(long value) implements Comparable<TelegramMessageId> {

    public TelegramMessageId {
        if (value <= 0) {
            throw new DomainException("Telegram message id must be positive: " + value);
        }
    }

    public boolean isAfter(TelegramMessageId other) {
        return other == null || value > other.value;
    }

    @Override
    public int compareTo(TelegramMessageId other) {
        return Long.compare(value, other.value);
    }
}
