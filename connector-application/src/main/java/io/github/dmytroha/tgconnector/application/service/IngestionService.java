package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;
import io.github.dmytroha.tgconnector.application.port.in.PollChannelsUseCase;
import io.github.dmytroha.tgconnector.application.port.in.ReceiveBotMessageUseCase;
import io.github.dmytroha.tgconnector.application.port.out.ChannelFeedPort;
import io.github.dmytroha.tgconnector.application.port.out.DomainEventPublisher;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.DuplicateSourceReferenceException;
import io.github.dmytroha.tgconnector.domain.source.IngestionMode;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Brings Telegram messages into the domain, both push (bot updates) and pull (channel polling).
 * <p>
 * Bot updates, the scheduler and the REST "poll now" endpoint call it from different threads, so
 * changes to sources and messages are serialized with one lock (network fetches run outside it),
 * and only one poll runs at a time.
 */
public class IngestionService implements ReceiveBotMessageUseCase, PollChannelsUseCase {

    private final SourceRepository sources;
    private final MessageRepository messages;
    private final ChannelFeedPort channelFeed;
    private final DomainEventPublisher events;
    private final IngestionPolicy policy;
    private final Clock clock;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicBoolean pollInProgress = new AtomicBoolean();

    public IngestionService(SourceRepository sources, MessageRepository messages, ChannelFeedPort channelFeed,
                            DomainEventPublisher events, IngestionPolicy policy, Clock clock) {
        this.sources = sources;
        this.messages = messages;
        this.channelFeed = channelFeed;
        this.events = events;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public Outcome receive(BotMessageCommand command) {
        var reference = new ChatReference.ChatId(command.chatId());
        writeLock.lock();
        try {
            var source = sources.findByReference(reference).or(() -> autoRegister(reference, command.chatTitle()));
            if (source.isEmpty()) {
                return Outcome.UNKNOWN_CHAT_IGNORED;
            }
            return store(source.get(), List.of(command.message())) > 0 ? Outcome.STORED : Outcome.DUPLICATE_OR_PAUSED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PollReport pollAll() {
        if (!pollInProgress.compareAndSet(false, true)) {
            return new PollReport(0, 0, List.of("poll already in progress"));
        }
        try {
            return doPollAll();
        } finally {
            pollInProgress.set(false);
        }
    }

    private PollReport doPollAll() {
        var polled = sources.findActiveByIngestionMode(IngestionMode.POLLING);
        var stored = 0;
        var failures = new ArrayList<String>();
        for (var source : polled) {
            try {
                var channel = (ChatReference.ChannelUsername) source.reference();
                var fetched = channelFeed.fetch(channel, source.lastReadMessageId());
                writeLock.lock();
                try {
                    // re-read: the source may have been paused while we were fetching
                    var current = sources.findById(source.id()).orElse(source);
                    stored += store(current, fetched);
                } finally {
                    writeLock.unlock();
                }
            } catch (RuntimeException e) {
                failures.add(source.reference().asString() + ": " + e.getMessage());
            }
        }
        return new PollReport(polled.size(), stored, failures);
    }

    private Optional<Source> autoRegister(ChatReference.ChatId reference, String title) {
        if (!policy.autoRegisterBotChats()) {
            return Optional.empty();
        }
        var source = Source.register(reference, title, clock);
        try {
            sources.save(source);
        } catch (DuplicateSourceReferenceException e) {
            return sources.findByReference(reference); // registered concurrently via REST
        }
        events.publish(source.pullEvents());
        return Optional.of(source);
    }

    private int store(Source source, List<IncomingMessage> incoming) {
        var stored = 0;
        var ordered = incoming.stream().sorted(Comparator.comparing(IncomingMessage::messageId)).toList();
        for (var in : ordered) {
            var accepted = source.accept(in.messageId(), in.content(), in.author(), in.postedAt(), clock);
            if (accepted.isPresent() && !messages.exists(accepted.get().id())) {
                messages.save(accepted.get());
                events.publish(accepted.get().pullEvents());
                stored++;
            }
        }
        sources.save(source);
        events.publish(source.pullEvents());
        return stored;
    }
}
