package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.source.SourceId;

import java.util.List;

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
}
