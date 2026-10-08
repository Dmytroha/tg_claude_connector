package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;
import io.github.dmytroha.tgconnector.application.port.in.PollChannelsUseCase;
import io.github.dmytroha.tgconnector.application.port.in.ReceiveBotMessageUseCase;
import io.github.dmytroha.tgconnector.application.port.out.ChannelFeedPort;
import io.github.dmytroha.tgconnector.application.port.out.DomainEventPublisher;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.IngestionMode;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Brings Telegram messages into the domain, both push (bot updates) and pull (channel polling).
 */
public class IngestionService implements ReceiveBotMessageUseCase, PollChannelsUseCase {

    private final SourceRepository sources;
    private final MessageRepository messages;
    private final ChannelFeedPort channelFeed;
    private final DomainEventPublisher events;
    private final IngestionPolicy policy;
    private final Clock clock;

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
        var source = sources.findByReference(reference).or(() -> autoRegister(reference, command.chatTitle()));
        if (source.isEmpty()) {
            return Outcome.UNKNOWN_CHAT_IGNORED;
        }
        return store(source.get(), List.of(command.message())) > 0 ? Outcome.STORED : Outcome.DUPLICATE_OR_PAUSED;
    }

    @Override
    public PollReport pollAll() {
        var polled = sources.findActiveByIngestionMode(IngestionMode.POLLING);
        var stored = 0;
        var failures = new ArrayList<String>();
        for (var source : polled) {
            try {
                var channel = (ChatReference.ChannelUsername) source.reference();
                stored += store(source, channelFeed.fetch(channel, source.lastReadMessageId()));
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
        sources.save(source);
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
