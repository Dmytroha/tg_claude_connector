package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.shared.DomainException;

/**
 * Identifies a Telegram chat the connector reads from.
 * <ul>
 *     <li>{@link ChannelUsername} — a public channel, read by polling its public feed;</li>
 *     <li>{@link ChatId} — any chat (private, group, channel) where our bot receives updates.</li>
 * </ul>
 */
public sealed interface ChatReference permits ChatReference.ChannelUsername, ChatReference.ChatId {

    IngestionMode ingestionMode();

    String asString();

    /**
     * Parses {@code @durov}, {@code durov}, {@code https://t.me/durov} or a numeric chat id like {@code -1001234567890}.
     */
    static ChatReference parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("Chat reference must not be blank");
        }
        var value = raw.strip();
        if (value.matches("-?\\d+")) {
            return new ChatId(Long.parseLong(value));
        }
        value = value.replaceFirst("^(https?://)?(t\\.me|telegram\\.me)/(s/)?", "").replaceFirst("^@", "");
        return new ChannelUsername(value);
    }

    record ChannelUsername(String value) implements ChatReference {

        private static final String PATTERN = "[A-Za-z][A-Za-z0-9_]{3,31}";

        public ChannelUsername {
            if (value == null || !value.matches(PATTERN)) {
                throw new DomainException("Invalid Telegram channel username: " + value);
            }
            value = value.toLowerCase();
        }

        @Override
        public IngestionMode ingestionMode() {
            return IngestionMode.POLLING;
        }

        @Override
        public String asString() {
            return "@" + value;
        }
    }

    record ChatId(long value) implements ChatReference {

        public ChatId {
            if (value == 0) {
                throw new DomainException("Chat id must not be 0");
            }
        }

        @Override
        public IngestionMode ingestionMode() {
            return IngestionMode.BOT_UPDATES;
        }

        @Override
        public String asString() {
            return Long.toString(value);
        }
    }
}
