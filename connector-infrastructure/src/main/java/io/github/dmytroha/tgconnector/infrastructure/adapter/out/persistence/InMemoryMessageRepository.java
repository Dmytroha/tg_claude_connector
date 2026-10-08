package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence;

import io.github.dmytroha.tgconnector.domain.message.Message;
import io.github.dmytroha.tgconnector.domain.message.MessageKey;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Driven adapter: non-durable storage, good enough for the template.
 */
@Repository
class InMemoryMessageRepository implements MessageRepository {

    private final Map<MessageKey, Message> store = new ConcurrentHashMap<>();

    @Override
    public Message save(Message message) {
        store.put(message.id(), message);
        return message;
    }

    @Override
    public boolean exists(MessageKey key) {
        return store.containsKey(key);
    }

    @Override
    public List<Message> findLatestBySource(SourceId sourceId, int limit) {
        return store.values().stream()
                .filter(m -> m.id().sourceId().equals(sourceId))
                .sorted(Comparator.comparing((Message m) -> m.id().telegramMessageId()).reversed())
                .limit(limit)
                .toList();
    }
}
