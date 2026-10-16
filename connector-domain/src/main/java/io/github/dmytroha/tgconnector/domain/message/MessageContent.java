package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.shared.DomainException;

import java.util.List;
import java.util.Set;

public record MessageContent(String text, List<Attachment> attachments) {

    public MessageContent {
        text = text == null ? "" : text;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        if (text.isBlank() && attachments.isEmpty()) {
            throw new DomainException("Message must contain text or at least one attachment");
        }
    }

    /** Hashtags of the text, e.g. {@code склад}, {@code реклама} (lower-case, without {@code #}). */
    public Set<String> hashtags() {
        return Hashtags.extract(text);
    }

    public static MessageContent text(String text) {
        return new MessageContent(text, List.of());
    }
}
