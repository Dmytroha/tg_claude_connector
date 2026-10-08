package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.application.dto.MessageView;
import io.github.dmytroha.tgconnector.application.exception.SourceNotFoundException;
import io.github.dmytroha.tgconnector.application.port.in.QueryMessagesUseCase;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;

import java.util.List;
import java.util.UUID;

public class MessageQueryService implements QueryMessagesUseCase {

    private static final int MAX_LIMIT = 500;

    private final SourceRepository sources;
    private final MessageRepository messages;

    public MessageQueryService(SourceRepository sources, MessageRepository messages) {
        this.sources = sources;
        this.messages = messages;
    }

    @Override
    public List<MessageView> latest(UUID sourceId, int limit) {
        var id = new SourceId(sourceId);
        if (sources.findById(id).isEmpty()) {
            throw new SourceNotFoundException(sourceId);
        }
        return messages.findLatestBySource(id, Math.clamp(limit, 1, MAX_LIMIT)).stream()
                .map(MessageView::from)
                .toList();
    }
}
