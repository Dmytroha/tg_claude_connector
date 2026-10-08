package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.shared.AggregateRoot;
import io.github.dmytroha.tgconnector.domain.shared.Guard;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;

import java.time.Clock;
import java.time.Instant;

/**
 * Aggregate root: a single message read from a Telegram source. Immutable once received.
 * Created only through {@link io.github.dmytroha.tgconnector.domain.source.Source#accept}.
 */
public class Message extends AggregateRoot<MessageKey> {

    private final MessageKey key;
    private final MessageContent content;
    private final Author author;
    private final Instant postedAt;
    private final Instant receivedAt;

    private Message(MessageKey key, MessageContent content, Author author, Instant postedAt, Instant receivedAt) {
        this.key = Guard.notNull(key, "key");
        this.content = Guard.notNull(content, "content");
        this.author = author == null ? Author.unknown() : author;
        this.postedAt = Guard.notNull(postedAt, "postedAt");
        this.receivedAt = Guard.notNull(receivedAt, "receivedAt");
    }

    public static Message receive(SourceId sourceId, TelegramMessageId messageId, MessageContent content,
                                  Author author, Instant postedAt, Clock clock) {
        var now = clock.instant();
        var message = new Message(new MessageKey(sourceId, messageId), content, author, postedAt, now);
        message.raise(new MessageReceived(message.key, postedAt, now));
        return message;
    }

    public static Message restore(MessageKey key, MessageContent content, Author author,
                                  Instant postedAt, Instant receivedAt) {
        return new Message(key, content, author, postedAt, receivedAt);
    }

    @Override
    public MessageKey id() {
        return key;
    }

    public MessageContent content() {
        return content;
    }

    public Author author() {
        return author;
    }

    public Instant postedAt() {
        return postedAt;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
