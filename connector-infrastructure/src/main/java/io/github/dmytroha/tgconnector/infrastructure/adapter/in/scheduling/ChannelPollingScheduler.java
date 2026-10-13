package io.github.dmytroha.tgconnector.infrastructure.adapter.in.scheduling;

import io.github.dmytroha.tgconnector.application.port.in.PollChannelsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Driving adapter: periodically triggers polling of public channels.
 */
@Component
@ConditionalOnProperty(prefix = "connector.telegram.polling", name = "enabled", havingValue = "true", matchIfMissing = true)
class ChannelPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChannelPollingScheduler.class);

    private final PollChannelsUseCase pollChannels;

    ChannelPollingScheduler(PollChannelsUseCase pollChannels) {
        this.pollChannels = pollChannels;
    }

    @Scheduled(fixedDelayString = "${connector.telegram.polling.interval:60s}", initialDelayString = "5s")
    void poll() {
        var report = pollChannels.pollAll();
        if (report.sourcesPolled() > 0) {
            log.info("Polled {} channel(s), stored {} new message(s)", report.sourcesPolled(), report.messagesStored());
        }
        report.failures().forEach(failure -> log.warn("Channel polling failed: {}", failure));
    }
}
