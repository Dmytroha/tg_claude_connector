package io.github.dmytroha.tgconnector.infrastructure.adapter.out.events;

import io.github.dmytroha.tgconnector.domain.shared.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Example subscriber. Put your downstream integration (webhook, broker, LLM pipeline, ...) here.
 */
@Component
class LoggingDomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventListener.class);

    @EventListener
    void on(DomainEvent event) {
        log.info("Domain event: {}", event);
    }
}
