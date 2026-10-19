package io.github.dmytroha.tgconnector.application.port.in;

import java.time.Duration;
import java.util.List;

/**
 * Pull-style ingestion: fetch new posts from every active polled channel.
 */
public interface PollChannelsUseCase {

    record PollReport(int sourcesPolled, int messagesStored, List<String> failures) {
    }

    /**
     * Polls now, unless another poll is already running.
     */
    PollReport pollAll();

    /**
     * Polls only if the last poll finished longer than {@code maxAge} ago (or never ran), so reads can
     * refresh on demand without hitting Telegram on every call. Waits for a poll already in progress.
     */
    PollReport pollIfOlderThan(Duration maxAge);
}
