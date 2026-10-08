package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.shared.DomainException;

/**
 * A chat can be registered as a source only once.
 */
public class DuplicateSourceReferenceException extends DomainException {

    public DuplicateSourceReferenceException(ChatReference reference) {
        super("Source already registered: " + reference.asString());
    }
}
