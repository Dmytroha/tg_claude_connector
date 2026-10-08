package io.github.dmytroha.tgconnector.domain.shared;

/**
 * Thrown when a domain invariant or business rule is violated.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
