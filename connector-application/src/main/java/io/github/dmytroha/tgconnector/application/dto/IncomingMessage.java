package io.github.dmytroha.tgconnector.application.dto;

import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;

import java.time.Instant;

/**
 * A message as delivered by any Telegram transport, already translated into domain vocabulary.
 */
public record IncomingMessage(TelegramMessageId messageId, MessageContent content, Author author, Instant postedAt) {
}
