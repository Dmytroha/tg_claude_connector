package io.github.dmytroha.tgconnector.application.port.in;

import java.time.Duration;

/**
 * Retention: deletes messages that are too old to be useful, so the database does not grow forever.
 */
public interface PurgeMessagesUseCase {

    /**
     * @param maxAge messages posted longer ago than this are deleted
     * @return how many messages were deleted
     */
    int purgeOlderThan(Duration maxAge);
}
