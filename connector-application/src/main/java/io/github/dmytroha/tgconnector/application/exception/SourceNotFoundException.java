package io.github.dmytroha.tgconnector.application.exception;

import java.util.UUID;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(UUID id) {
        super("Source not found: " + id);
    }
}
