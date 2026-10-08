package io.github.dmytroha.tgconnector.infrastructure.adapter.out.events;

import io.github.dmytroha.tgconnector.application.port.out.DomainEventPublisher;
import io.github.dmytroha.tgconnector.domain.shared.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Driven adapter: publishes domain events in-process via Spring.
 * Swap for a Kafka/RabbitMQ adapter to forward messages to downstream systems.
 */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Collection<? extends DomainEvent> events) {
        events.forEach(publisher::publishEvent);
    }
}
