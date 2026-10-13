package io.github.dmytroha.tgconnector.bootstrap;

import io.github.dmytroha.tgconnector.application.port.out.ChannelFeedPort;
import io.github.dmytroha.tgconnector.application.port.out.DomainEventPublisher;
import io.github.dmytroha.tgconnector.application.service.IngestionPolicy;
import io.github.dmytroha.tgconnector.application.service.IngestionService;
import io.github.dmytroha.tgconnector.application.service.MessageQueryService;
import io.github.dmytroha.tgconnector.application.service.SourceService;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;
import io.github.dmytroha.tgconnector.infrastructure.config.TelegramProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root: application services are plain Java, so they are wired here
 * instead of being annotated with Spring stereotypes.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IngestionPolicy ingestionPolicy(TelegramProperties properties) {
        return new IngestionPolicy(properties.bot().autoRegisterChats());
    }

    @Bean
    SourceService sourceService(SourceRepository sources, DomainEventPublisher events, Clock clock) {
        return new SourceService(sources, events, clock);
    }

    @Bean
    IngestionService ingestionService(SourceRepository sources, MessageRepository messages, ChannelFeedPort channelFeed,
                                      DomainEventPublisher events, IngestionPolicy policy, Clock clock) {
        return new IngestionService(sources, messages, channelFeed, events, policy, clock);
    }

    @Bean
    MessageQueryService messageQueryService(SourceRepository sources, MessageRepository messages) {
        return new MessageQueryService(sources, messages);
    }
}
