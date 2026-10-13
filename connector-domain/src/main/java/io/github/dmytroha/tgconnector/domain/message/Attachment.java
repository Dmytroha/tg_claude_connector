package io.github.dmytroha.tgconnector.domain.message;

import io.github.dmytroha.tgconnector.domain.shared.Guard;

/**
 * Media attached to a message. {@code reference} is a Telegram file id or a URL depending on the source.
 */
public record Attachment(Kind kind, String reference) {

    public enum Kind { PHOTO, VIDEO, DOCUMENT, AUDIO, VOICE, STICKER, OTHER }

    public Attachment {
        Guard.notNull(kind, "kind");
        Guard.notBlank(reference, "reference");
    }
}
