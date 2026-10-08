package io.github.dmytroha.tgconnector.application.dto;

import io.github.dmytroha.tgconnector.domain.message.Message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageView(UUID sourceId, long messageId, String text, List<AttachmentView> attachments,
                          String author, Instant postedAt, Instant receivedAt) {

    public record AttachmentView(String kind, String reference) {
    }

    public static MessageView from(Message message) {
        return new MessageView(
                message.id().sourceId().value(),
                message.id().telegramMessageId().value(),
                message.content().text(),
                message.content().attachments().stream()
                        .map(a -> new AttachmentView(a.kind().name(), a.reference()))
                        .toList(),
                message.author().displayName(),
                message.postedAt(),
                message.receivedAt());
    }
}
