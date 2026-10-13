package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.shared.DomainException;

/**
 * The source was changed by someone else after it was loaded (optimistic locking failure).
 */
public class ConcurrentSourceModificationException extends DomainException {

    public ConcurrentSourceModificationException(SourceId id) {
        super("Source was modified concurrently: " + id);
    }
}
