package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;
import io.github.dmytroha.tgconnector.application.port.in.ReceiveBotMessageUseCase.BotMessageCommand;
import io.github.dmytroha.tgconnector.application.port.in.ReceiveBotMessageUseCase.Outcome;
import io.github.dmytroha.tgconnector.application.port.out.ChannelFeedPort;
import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.Message;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.message.MessageCriteria;
import io.github.dmytroha.tgconnector.domain.message.MessageKey;
import io.github.dmytroha.tgconnector.domain.message.MessageReceived;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.shared.DomainEvent;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.IngestionMode;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final FakeSources sources = new FakeSources();
    private final FakeMessages messages = new FakeMessages();
    private final List<DomainEvent> published = new ArrayList<>();

    @Test
    void pollsChannelsAndStoresOnlyNewMessages() {
        var channel = Source.register(ChatReference.parse("@telegram"), null, clock);
        sources.save(channel);
        ChannelFeedPort feed = (username, after) -> List.of(incoming(2), incoming(1), incoming(3));
        var service = service(feed, false);

        var first = service.pollAll();
        var second = service.pollAll();

        assertThat(first.messagesStored()).isEqualTo(3);
        assertThat(second.messagesStored()).isZero();
        assertThat(published).filteredOn(MessageReceived.class::isInstance).hasSize(3);
    }

    @Test
    void reportsFeedFailuresWithoutStoppingOtherSources() {
        sources.save(Source.register(ChatReference.parse("@broken"), null, clock));
        ChannelFeedPort feed = (username, after) -> {
            throw new IllegalStateException("boom");
        };

        var report = service(feed, false).pollAll();

        assertThat(report.failures()).singleElement().asString().contains("@broken", "boom");
    }

    @Test
    void ignoresUnknownBotChatUnlessAutoRegistrationIsEnabled() {
        var command = new BotMessageCommand(-100L, "Group", incoming(1));

        assertThat(service(null, false).receive(command)).isEqualTo(Outcome.UNKNOWN_CHAT_IGNORED);
        assertThat(service(null, true).receive(command)).isEqualTo(Outcome.STORED);
        assertThat(sources.findByReference(new ChatReference.ChatId(-100L))).get()
                .extracting(Source::title).isEqualTo("Group");
    }

    private IngestionService service(ChannelFeedPort feed, boolean autoRegister) {
        return new IngestionService(sources, messages, feed, published::addAll, new IngestionPolicy(autoRegister), clock);
    }

    private IncomingMessage incoming(long id) {
        return new IncomingMessage(new TelegramMessageId(id), MessageContent.text("post " + id), Author.unknown(), clock.instant());
    }

    static class FakeSources implements SourceRepository {
        final Map<SourceId, Source> store = new HashMap<>();

        public Source save(Source source) {
            store.put(source.id(), source);
            return source;
        }

        public Optional<Source> findById(SourceId id) {
            return Optional.ofNullable(store.get(id));
        }

        public Optional<Source> findByReference(ChatReference reference) {
            return store.values().stream().filter(s -> s.reference().equals(reference)).findFirst();
        }

        public List<Source> findActiveByIngestionMode(IngestionMode mode) {
            return store.values().stream().filter(s -> s.isActive() && s.ingestionMode() == mode).toList();
        }

        public List<Source> findAll() {
            return List.copyOf(store.values());
        }
    }

    static class FakeMessages implements MessageRepository {
        final Map<MessageKey, Message> store = new HashMap<>();

        public Message save(Message message) {
            store.put(message.id(), message);
            return message;
        }

        public boolean exists(MessageKey key) {
            return store.containsKey(key);
        }

        public List<Message> findLatestBySource(SourceId sourceId, int limit) {
            return store.values().stream().filter(m -> m.id().sourceId().equals(sourceId))
                    .sorted(Comparator.comparing((Message m) -> m.id().telegramMessageId()).reversed())
                    .limit(limit).toList();
        }

        public List<Message> search(MessageCriteria criteria) {
            return store.values().stream().filter(criteria::matches)
                    .sorted(Comparator.comparing(Message::postedAt).reversed())
                    .limit(criteria.limit()).toList();
        }
    }
}
