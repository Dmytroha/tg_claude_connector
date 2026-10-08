package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.shared.DomainException;

import java.util.List;

public record MessageContent(String text, List<Attachment> attachments) {

    public MessageContent {
        text = text == null ? "" : text;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        if (text.isBlank() && attachments.isEmpty()) {
            throw new DomainException("Message must contain text or at least one attachment");
        }
    }

    public static MessageContent text(String text) {
        return new MessageContent(text, List.of());
    }
}
