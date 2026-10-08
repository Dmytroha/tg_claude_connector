package io.github.dmytroha.tgconnector.application.port.out;

import io.github.dmytroha.tgconnector.domain.shared.DomainEvent;

import java.util.Collection;

public interface DomainEventPublisher {

    void publish(Collection<? extends DomainEvent> events);
}
