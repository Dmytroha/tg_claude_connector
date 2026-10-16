package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.source.SourceId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MessageRepository {

    /**
     * Stores a new message. Saving a message that already exists is a no-op.
     */
    Message save(Message message);

    boolean exists(MessageKey key);

    /**
     * Most recent messages first.
     */
    List<Message> findLatestBySource(SourceId sourceId, int limit);

    /**
     * Messages matching the criteria, newest first (by posting time).
     */
    List<Message> search(MessageCriteria criteria);

    /**
     * How many messages carry each hashtag, most used first.
     *
     * @param sourceIds  restrict to these sources; empty — all sources
     * @param postedFrom only messages posted at or after this moment
     */
    List<HashtagCount> countHashtags(Set<SourceId> sourceIds, Optional<Instant> postedFrom);

    record HashtagCount(String hashtag, long messages) {
    }
}
