package io.github.dmytroha.tgconnector.application.service;

/**
 * Tunable application rules for ingestion.
 *
 * @param autoRegisterBotChats register a new source automatically when the bot receives
 *                             a message from a chat that is not registered yet
 */
public record IngestionPolicy(boolean autoRegisterBotChats) {
}
