package io.github.dmytroha.tgconnector.application.port.in;

import java.util.List;

/**
 * Pull-style ingestion: fetch new posts from every active polled channel.
 */
public interface PollChannelsUseCase {

    record PollReport(int sourcesPolled, int messagesStored, List<String> failures) {
    }

    PollReport pollAll();
}
