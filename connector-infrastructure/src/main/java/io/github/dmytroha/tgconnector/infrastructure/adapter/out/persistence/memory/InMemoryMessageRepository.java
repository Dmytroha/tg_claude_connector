package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence.memory;

import io.github.dmytroha.tgconnector.domain.message.Message;
import io.github.dmytroha.tgconnector.domain.message.MessageCriteria;
import io.github.dmytroha.tgconnector.domain.message.MessageKey;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * Driven adapter: non-durable storage, good enough for the template. Messages are indexed per source
 * and ordered by id, so reading the latest page costs O(limit); search scans everything.
 */
@Repository
@ConditionalOnProperty(prefix = "connector", name = "persistence", havingValue = "memory")
class InMemoryMessageRepository implements MessageRepository {

    private final Map<SourceId, NavigableMap<TelegramMessageId, Message>> bySource = new ConcurrentHashMap<>();

    @Override
    public Message save(Message message) {
        bySource.computeIfAbsent(message.id().sourceId(), id -> new ConcurrentSkipListMap<>(Comparator.reverseOrder()))
                .putIfAbsent(message.id().telegramMessageId(), message);
        return message;
    }

    @Override
    public boolean exists(MessageKey key) {
        var messages = bySource.get(key.sourceId());
        return messages != null && messages.containsKey(key.telegramMessageId());
    }

    @Override
    public List<Message> findLatestBySource(SourceId sourceId, int limit) {
        var messages = bySource.get(sourceId);
        return messages == null ? List.of() : messages.values().stream().limit(limit).toList();
    }

    @Override
    public List<HashtagCount> countHashtags(Set<SourceId> sourceIds, Optional<Instant> postedFrom) {
        return bySource.entrySet().stream()
                .filter(entry -> sourceIds.isEmpty() || sourceIds.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().values().stream())
                .filter(m -> postedFrom.map(from -> !m.postedAt().isBefore(from)).orElse(true))
                .flatMap(m -> m.content().hashtags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new HashtagCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(HashtagCount::messages).reversed().thenComparing(HashtagCount::hashtag))
                .toList();
    }

    @Override
    public int deletePostedBefore(Instant cutoff) {
        var deleted = 0;
        for (var messages : bySource.values()) {
            for (var message : messages.values()) {
                if (message.postedAt().isBefore(cutoff) && messages.remove(message.id().telegramMessageId(), message)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    @Override
    public List<Message> search(MessageCriteria criteria) {
        return bySource.values().stream()
                .flatMap(messages -> messages.values().stream())
                .filter(criteria::matches)
                .sorted(Comparator.comparing(Message::postedAt).reversed())
                .limit(criteria.limit())
                .toList();
    }
}
