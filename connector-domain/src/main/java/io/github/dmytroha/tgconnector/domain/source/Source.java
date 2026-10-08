package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.Message;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.shared.AggregateRoot;
import io.github.dmytroha.tgconnector.domain.shared.Guard;
import io.github.dmytroha.tgconnector.domain.source.SourceEvents.SourcePaused;
import io.github.dmytroha.tgconnector.domain.source.SourceEvents.SourceRegistered;
import io.github.dmytroha.tgconnector.domain.source.SourceEvents.SourceResumed;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Aggregate root: a Telegram chat (channel / group / bot chat) the connector reads.
 * <p>
 * Owns the read cursor and guarantees that each Telegram message is accepted at most once
 * and only while the source is active.
 */
public class Source extends AggregateRoot<SourceId> {

    private final SourceId id;
    private final ChatReference reference;
    private String title;
    private SourceStatus status;
    private TelegramMessageId lastReadMessageId;
    private final Instant registeredAt;

    private Source(SourceId id, ChatReference reference, String title, SourceStatus status,
                   TelegramMessageId lastReadMessageId, Instant registeredAt) {
        this.id = Guard.notNull(id, "id");
        this.reference = Guard.notNull(reference, "reference");
        this.title = title == null || title.isBlank() ? reference.asString() : title;
        this.status = Guard.notNull(status, "status");
        this.lastReadMessageId = lastReadMessageId;
        this.registeredAt = Guard.notNull(registeredAt, "registeredAt");
    }

    /**
     * Factory for a brand-new source.
     */
    public static Source register(ChatReference reference, String title, Clock clock) {
        var now = clock.instant();
        var source = new Source(SourceId.newId(), reference, title, SourceStatus.ACTIVE, null, now);
        source.raise(new SourceRegistered(source.id, reference, now));
        return source;
    }

    /**
     * Rehydrates a source from persistence. Raises no events.
     */
    public static Source restore(SourceId id, ChatReference reference, String title, SourceStatus status,
                                 TelegramMessageId lastReadMessageId, Instant registeredAt) {
        return new Source(id, reference, title, status, lastReadMessageId, registeredAt);
    }

    public void pause(Clock clock) {
        if (status == SourceStatus.PAUSED) {
            return;
        }
        status = SourceStatus.PAUSED;
        raise(new SourcePaused(id, clock.instant()));
    }

    public void resume(Clock clock) {
        if (status == SourceStatus.ACTIVE) {
            return;
        }
        status = SourceStatus.ACTIVE;
        raise(new SourceResumed(id, clock.instant()));
    }

    public void rename(String newTitle) {
        if (newTitle != null && !newTitle.isBlank()) {
            this.title = newTitle;
        }
    }

    public boolean isActive() {
        return status == SourceStatus.ACTIVE;
    }

    /**
     * Accepts a message coming from Telegram. Acts as a factory for the {@link Message} aggregate
     * and advances the read cursor.
     *
     * @return the new message, or empty if the source is paused or the message was already read
     */
    public Optional<Message> accept(TelegramMessageId messageId, MessageContent content, Author author,
                                    Instant postedAt, Clock clock) {
        Guard.notNull(messageId, "messageId");
        if (!isActive() || !messageId.isAfter(lastReadMessageId)) {
            return Optional.empty();
        }
        lastReadMessageId = messageId;
        return Optional.of(Message.receive(id, messageId, content, author, postedAt, clock));
    }

    @Override
    public SourceId id() {
        return id;
    }

    public ChatReference reference() {
        return reference;
    }

    public IngestionMode ingestionMode() {
        return reference.ingestionMode();
    }

    public String title() {
        return title;
    }

    public SourceStatus status() {
        return status;
    }

    public Optional<TelegramMessageId> lastReadMessageId() {
        return Optional.ofNullable(lastReadMessageId);
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}
