package io.github.dmytroha.tgconnector.application.port.in;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;

/**
 * Push-style ingestion: the Telegram bot delivered a message from a chat it is a member of.
 */
public interface ReceiveBotMessageUseCase {

    record BotMessageCommand(long chatId, String chatTitle, IncomingMessage message) {
    }

    enum Outcome { STORED, DUPLICATE_OR_PAUSED, UNKNOWN_CHAT_IGNORED }

    Outcome receive(BotMessageCommand command);
}
