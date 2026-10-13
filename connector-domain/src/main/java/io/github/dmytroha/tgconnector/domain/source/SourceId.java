package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.shared.Guard;

import java.util.UUID;

public record SourceId(UUID value) {

    public SourceId {
        Guard.notNull(value, "SourceId");
    }

    public static SourceId newId() {
        return new SourceId(UUID.randomUUID());
    }

    public static SourceId of(String value) {
        return new SourceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
