package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.application.port.in.PurgeMessagesUseCase;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;

import java.time.Clock;
import java.time.Duration;

/**
 * Deletes old messages by posting time. Sources keep their read cursors, so deleted posts are not fetched again.
 */
public class MessageRetentionService implements PurgeMessagesUseCase {

    private final MessageRepository messages;
    private final Clock clock;

    public MessageRetentionService(MessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    @Override
    public int purgeOlderThan(Duration maxAge) {
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("maxAge must be positive, got " + maxAge);
        }
        return messages.deletePostedBefore(clock.instant().minus(maxAge));
    }
}
