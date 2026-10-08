package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence;

import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.IngestionMode;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;
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
class InMemorySourceRepository implements SourceRepository {

    private final Map<SourceId, Source> store = new ConcurrentHashMap<>();

    @Override
    public Source save(Source source) {
        store.put(source.id(), source);
        return source;
    }

    @Override
    public Optional<Source> findById(SourceId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Source> findByReference(ChatReference reference) {
        return store.values().stream().filter(s -> s.reference().equals(reference)).findFirst();
    }

    @Override
    public List<Source> findActiveByIngestionMode(IngestionMode mode) {
        return store.values().stream()
                .filter(s -> s.isActive() && s.ingestionMode() == mode)
                .sorted(Comparator.comparing(Source::registeredAt))
                .toList();
    }

    @Override
    public List<Source> findAll() {
        return store.values().stream().sorted(Comparator.comparing(Source::registeredAt)).toList();
    }
}
