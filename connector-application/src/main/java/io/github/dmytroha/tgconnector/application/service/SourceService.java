package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.application.dto.SourceView;
import io.github.dmytroha.tgconnector.application.exception.SourceAlreadyRegisteredException;
import io.github.dmytroha.tgconnector.application.exception.SourceNotFoundException;
import io.github.dmytroha.tgconnector.application.port.in.ManageSourceUseCase;
import io.github.dmytroha.tgconnector.application.port.in.QuerySourcesUseCase;
import io.github.dmytroha.tgconnector.application.port.in.RegisterSourceUseCase;
import io.github.dmytroha.tgconnector.application.port.out.DomainEventPublisher;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.DuplicateSourceReferenceException;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SourceService implements RegisterSourceUseCase, ManageSourceUseCase, QuerySourcesUseCase {

    private final SourceRepository sources;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SourceService(SourceRepository sources, DomainEventPublisher events, Clock clock) {
        this.sources = sources;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public SourceView register(RegisterSourceCommand command) {
        var reference = ChatReference.parse(command.reference());
        if (sources.findByReference(reference).isPresent()) {
            throw new SourceAlreadyRegisteredException(reference.asString());
        }
        var source = Source.register(reference, command.title(), clock);
        try {
            sources.save(source);
        } catch (DuplicateSourceReferenceException e) {
            throw new SourceAlreadyRegisteredException(reference.asString()); // lost a concurrent registration race
        }
        events.publish(source.pullEvents());
        return SourceView.from(source);
    }

    @Override
    public SourceView pause(UUID sourceId) {
        return change(sourceId, s -> s.pause(clock));
    }

    @Override
    public SourceView resume(UUID sourceId) {
        return change(sourceId, s -> s.resume(clock));
    }

    @Override
    public List<SourceView> findAll() {
        return sources.findAll().stream().map(SourceView::from).toList();
    }

    @Override
    public SourceView get(UUID sourceId) {
        return SourceView.from(load(sourceId));
    }

    private SourceView change(UUID sourceId, Consumer<Source> action) {
        var source = load(sourceId);
        action.accept(source);
        sources.save(source);
        events.publish(source.pullEvents());
        return SourceView.from(source);
    }

    private Source load(UUID sourceId) {
        return sources.findById(new SourceId(sourceId)).orElseThrow(() -> new SourceNotFoundException(sourceId));
    }
}
