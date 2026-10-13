package io.github.dmytroha.tgconnector.application.exception;

import java.util.UUID;

public class SourceConcurrentlyModifiedException extends RuntimeException {

    public SourceConcurrentlyModifiedException(UUID id) {
        super("Source was modified concurrently, retry the request: " + id);
    }
}
