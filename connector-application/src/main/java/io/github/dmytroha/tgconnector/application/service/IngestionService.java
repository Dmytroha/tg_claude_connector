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
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Brings Telegram messages into the domain, both push (bot updates) and pull (channel polling).
 * <p>
 * Bot updates, the scheduler, MCP reads and the REST "poll now" endpoint call it from different threads,
 * so changes to sources and messages are serialized with one lock (network fetches run outside it),
 * and only one poll runs at a time.
 * <p>
 * Channels are polled rarely (a few times a day, plus on demand), so one poll reads page after page
 * until it catches up with the channel, at most {@link #MAX_PAGES_PER_POLL} pages per channel.
 */
public class IngestionService implements ReceiveBotMessageUseCase, PollChannelsUseCase {

    static final int MAX_PAGES_PER_POLL = 10;

    private final SourceRepository sources;
    private final MessageRepository messages;
    private final ChannelFeedPort channelFeed;
    private final DomainEventPublisher events;
    private final IngestionPolicy policy;
    private final Clock clock;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock pollLock = new ReentrantLock();
    private volatile Instant lastPollFinishedAt;

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
        if (!pollLock.tryLock()) {
            return new PollReport(0, 0, List.of("poll already in progress"));
        }
        try {
            return doPollAll();
        } finally {
            pollLock.unlock();
        }
    }

    @Override
    public PollReport pollIfOlderThan(Duration maxAge) {
        if (isFresh(maxAge)) {
            return new PollReport(0, 0, List.of());
        }
        pollLock.lock();
        try {
            // a poll that was running while we waited may have just refreshed everything
            return isFresh(maxAge) ? new PollReport(0, 0, List.of()) : doPollAll();
        } finally {
            pollLock.unlock();
        }
    }

    private boolean isFresh(Duration maxAge) {
        var last = lastPollFinishedAt;
        return last != null && last.plus(maxAge).isAfter(clock.instant());
    }

    private PollReport doPollAll() {
        var polled = sources.findActiveByIngestionMode(IngestionMode.POLLING);
        var stored = 0;
        var failures = new ArrayList<String>();
        for (var source : polled) {
            try {
                stored += pollChannel(source);
            } catch (RuntimeException e) {
                failures.add(source.reference().asString() + ": " + e.getMessage());
            }
        }
        lastPollFinishedAt = clock.instant();
        return new PollReport(polled.size(), stored, failures);
    }

    /**
     * Reads pages newer than the cursor until a page brings nothing new.
     */
    private int pollChannel(Source source) {
        var channel = (ChatReference.ChannelUsername) source.reference();
        var cursor = source.lastReadMessageId();
        var stored = 0;
        for (var page = 0; page < MAX_PAGES_PER_POLL; page++) {
            var fetched = channelFeed.fetch(channel, cursor);
            if (fetched.isEmpty()) {
                break;
            }
            Optional<TelegramMessageId> advanced;
            writeLock.lock();
            try {
                // re-read: the source may have been paused while we were fetching
                var current = sources.findById(source.id()).orElse(source);
                stored += store(current, fetched);
                advanced = current.lastReadMessageId();
            } finally {
                writeLock.unlock();
            }
            // no progress: paused, or the feed has nothing newer; the first page without a cursor is
            // only the latest posts, there is no older history to backfill
            if (advanced.equals(cursor) || cursor.isEmpty()) {
                break;
            }
            cursor = advanced;
        }
        return stored;
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
