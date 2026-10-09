package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.application.dto.MessageView;
import io.github.dmytroha.tgconnector.application.exception.SourceNotFoundException;
import io.github.dmytroha.tgconnector.application.port.in.QueryMessagesUseCase;
import io.github.dmytroha.tgconnector.application.port.in.SearchMessagesUseCase;
import io.github.dmytroha.tgconnector.domain.message.MessageCriteria;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class MessageQueryService implements QueryMessagesUseCase, SearchMessagesUseCase {

    private static final int MAX_LIMIT = 500;

    private final SourceRepository sources;
    private final MessageRepository messages;

    public MessageQueryService(SourceRepository sources, MessageRepository messages) {
        this.sources = sources;
        this.messages = messages;
    }

    @Override
    public List<MessageView> latest(UUID sourceId, int limit) {
        var id = existing(sourceId);
        return messages.findLatestBySource(id, clampLimit(limit)).stream()
                .map(MessageView::from)
                .toList();
    }

    @Override
    public List<MessageView> search(SearchMessagesQuery query) {
        var sourceIds = query.sourceIds() == null ? List.<UUID>of() : query.sourceIds();
        var criteria = new MessageCriteria(
                Optional.ofNullable(query.text()),
                sourceIds.stream().map(this::existing).collect(Collectors.toSet()),
                Optional.ofNullable(query.since()),
                clampLimit(query.limit()));
        return messages.search(criteria).stream().map(MessageView::from).toList();
    }

    private SourceId existing(UUID sourceId) {
        var id = new SourceId(sourceId);
        if (sources.findById(id).isEmpty()) {
            throw new SourceNotFoundException(sourceId);
        }
        return id;
    }

    private static int clampLimit(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }
}
