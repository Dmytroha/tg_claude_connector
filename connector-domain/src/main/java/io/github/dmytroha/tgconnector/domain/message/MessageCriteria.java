package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.source.SourceId;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * What to look for among stored messages. Results are ordered newest first.
 *
 * @param text       case-insensitive substring of the message text; empty — any text
 * @param sourceIds  restrict to these sources; empty — all sources
 * @param postedFrom only messages posted at or after this moment; empty — no lower bound
 * @param hashtags   only messages with at least one of these hashtags; empty — no restriction
 * @param excludedHashtags skip messages with any of these hashtags (e.g. {@code реклама})
 * @param limit      maximum number of messages to return
 */
public record MessageCriteria(Optional<String> text, Set<SourceId> sourceIds, Optional<Instant> postedFrom,
                              Set<String> hashtags, Set<String> excludedHashtags, int limit) {

    public MessageCriteria(Optional<String> text, Set<SourceId> sourceIds, Optional<Instant> postedFrom, int limit) {
        this(text, sourceIds, postedFrom, Set.of(), Set.of(), limit);
    }

    public MessageCriteria {
        hashtags = Hashtags.normalize(hashtags);
        excludedHashtags = Hashtags.normalize(excludedHashtags);
        text = text == null ? Optional.empty() : text.map(String::strip).filter(t -> !t.isEmpty());
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        postedFrom = postedFrom == null ? Optional.empty() : postedFrom;
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
    }

    /**
     * Whether a message satisfies the criteria. Lets simple repositories filter in memory.
     */
    public boolean matches(Message message) {
        return (sourceIds.isEmpty() || sourceIds.contains(message.id().sourceId()))
                && postedFrom.map(from -> !message.postedAt().isBefore(from)).orElse(true)
                && text.map(t -> message.content().text().toLowerCase().contains(t.toLowerCase())).orElse(true)
                && matchesHashtags(message.content().hashtags());
    }

    private boolean matchesHashtags(Set<String> tags) {
        return (hashtags.isEmpty() || tags.stream().anyMatch(hashtags::contains))
                && tags.stream().noneMatch(excludedHashtags::contains);
    }
}
