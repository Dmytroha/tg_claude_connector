package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence.memory;

import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.DuplicateSourceReferenceException;
import io.github.dmytroha.tgconnector.domain.source.IngestionMode;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Driven adapter: non-durable storage, good enough for the template.
 * Replace with a JPA/JDBC adapter implementing {@link SourceRepository} for production.
 */
@Repository
@ConditionalOnProperty(prefix = "connector", name = "persistence", havingValue = "memory")
class InMemorySourceRepository implements SourceRepository {

    private final Map<SourceId, Source> byId = new ConcurrentHashMap<>();
    private final Map<ChatReference, SourceId> byReference = new ConcurrentHashMap<>();

    @Override
    public Source save(Source source) {
        // putIfAbsent acts as a unique constraint on the chat reference
        var owner = byReference.putIfAbsent(source.reference(), source.id());
        if (owner != null && !owner.equals(source.id())) {
            throw new DuplicateSourceReferenceException(source.reference());
        }
        byId.put(source.id(), source);
        source.markPersisted(source.version() + 1); // the same instance is shared, so no conflict check is needed
        return source;
    }

    @Override
    public Optional<Source> findById(SourceId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Source> findByReference(ChatReference reference) {
        return Optional.ofNullable(byReference.get(reference)).map(byId::get);
    }

    @Override
    public List<Source> findActiveByIngestionMode(IngestionMode mode) {
        return byId.values().stream()
                .filter(s -> s.isActive() && s.ingestionMode() == mode)
                .sorted(Comparator.comparing(Source::registeredAt))
                .toList();
    }

    @Override
    public List<Source> findAll() {
        return byId.values().stream().sorted(Comparator.comparing(Source::registeredAt)).toList();
    }
}
