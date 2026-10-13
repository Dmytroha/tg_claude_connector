package io.github.dmytroha.tgconnector.application.exception;

public class SourceAlreadyRegisteredException extends RuntimeException {

    public SourceAlreadyRegisteredException(String reference) {
        super("Source already registered: " + reference);
    }
}
