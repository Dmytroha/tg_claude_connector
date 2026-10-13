package io.github.dmytroha.tgconnector.domain.source;

/**
 * How messages of a source reach the connector.
 */
public enum IngestionMode {
    /** The connector pulls the source periodically (e.g. a public channel feed). */
    POLLING,
    /** Telegram pushes updates to our bot (bot is a member/admin of the chat). */
    BOT_UPDATES
}
