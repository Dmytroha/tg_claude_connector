package io.github.dmytroha.tgconnector.application.dto;

import io.github.dmytroha.tgconnector.domain.source.Source;

import java.time.Instant;
import java.util.UUID;

public record SourceView(UUID id, String reference, String title, String ingestionMode, String status,
                         Long lastReadMessageId, Instant registeredAt) {

    public static SourceView from(Source source) {
        return new SourceView(
                source.id().value(),
                source.reference().asString(),
                source.title(),
                source.ingestionMode().name(),
                source.status().name(),
                source.lastReadMessageId().map(id -> id.value()).orElse(null),
                source.registeredAt());
    }
}
