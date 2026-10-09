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
     * @param text      case-insensitive substring; {@code null} or blank — any text
     * @param sourceIds restrict to these sources; {@code null} or empty — all sources
     * @param since     only messages posted at or after this moment; {@code null} — no lower bound
     */
    record SearchMessagesQuery(String text, List<UUID> sourceIds, Instant since, int limit) {
    }

    List<MessageView> search(SearchMessagesQuery query);
}
