package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.shared.Guard;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;

/**
 * Natural identity of a message: Telegram message ids are only unique within one chat.
 */
public record MessageKey(SourceId sourceId, TelegramMessageId telegramMessageId) {

    public MessageKey {
        Guard.notNull(sourceId, "sourceId");
        Guard.notNull(telegramMessageId, "telegramMessageId");
    }
}
