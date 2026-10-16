package io.github.dmytroha.tgconnector.application.port.in;

import io.github.dmytroha.tgconnector.application.dto.MessageView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Finds stored messages across sources, newest first.
 */
public interface SearchMessagesUseCase {

    /**
     * @param text            case-insensitive substring; {@code null} or blank — any text
     * @param sourceIds       restrict to these sources; {@code null} or empty — all sources
     * @param since           only messages posted at or after this moment; {@code null} — no lower bound
     * @param hashtags        only messages with at least one of these hashtags ({@code #} optional)
     * @param excludeHashtags skip messages with any of these hashtags, e.g. {@code реклама}
     */
    record SearchMessagesQuery(String text, List<UUID> sourceIds, Instant since, List<String> hashtags,
                               List<String> excludeHashtags, int limit) {

        public SearchMessagesQuery(String text, List<UUID> sourceIds, Instant since, int limit) {
            this(text, sourceIds, since, List.of(), List.of(), limit);
        }
    }

    record HashtagView(String hashtag, long messages) {
    }

    List<MessageView> search(SearchMessagesQuery query);

    /**
     * Hashtags in use (channels' own categories) with message counts, most used first.
     */
    List<HashtagView> hashtags(List<UUID> sourceIds, Instant since);
}
