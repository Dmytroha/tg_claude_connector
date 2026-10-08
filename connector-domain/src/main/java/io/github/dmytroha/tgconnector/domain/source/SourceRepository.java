package io.github.dmytroha.tgconnector.domain.source;

import java.util.List;
import java.util.Optional;

/**
 * Collection-like contract for {@link Source} aggregates. Implemented by an infrastructure adapter.
 */
public interface SourceRepository {

    /**
     * Inserts or updates a source. Must be atomic with respect to the chat reference, like a unique
     * constraint in a database.
     *
     * @throws DuplicateSourceReferenceException if another source with the same reference exists
     */
    Source save(Source source);

    Optional<Source> findById(SourceId id);

    Optional<Source> findByReference(ChatReference reference);

    List<Source> findActiveByIngestionMode(IngestionMode mode);

    List<Source> findAll();
}
