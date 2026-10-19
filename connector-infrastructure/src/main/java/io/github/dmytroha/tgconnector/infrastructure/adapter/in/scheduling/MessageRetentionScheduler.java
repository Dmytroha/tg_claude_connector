package io.github.dmytroha.tgconnector.infrastructure.adapter.in.scheduling;

import io.github.dmytroha.tgconnector.application.port.in.PurgeMessagesUseCase;
import io.github.dmytroha.tgconnector.infrastructure.config.RetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Driving adapter: deletes old messages once a day (at night by default).
 */
@Component
@ConditionalOnProperty(prefix = "connector.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
class MessageRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(MessageRetentionScheduler.class);

    private final PurgeMessagesUseCase purgeMessages;
    private final RetentionProperties properties;

    MessageRetentionScheduler(PurgeMessagesUseCase purgeMessages, RetentionProperties properties) {
        this.purgeMessages = purgeMessages;
        this.properties = properties;
    }

    @Scheduled(cron = "${connector.retention.cron:0 30 4 * * *}", zone = "${connector.retention.zone:Europe/Prague}")
    void purge() {
        var deleted = purgeMessages.purgeOlderThan(properties.maxAge());
        log.info("Deleted {} message(s) older than {}", deleted, properties.maxAge());
    }
}
