package io.github.dmytroha.tgconnector.domain.message;

/**
 * Who posted the message. For channel posts this is usually the channel itself.
 */
public record Author(String displayName, String username) {

    public static Author unknown() {
        return new Author("unknown", null);
    }
}
