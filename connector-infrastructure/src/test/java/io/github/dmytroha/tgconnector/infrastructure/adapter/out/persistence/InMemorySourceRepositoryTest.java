package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence;

import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.DuplicateSourceReferenceException;
import io.github.dmytroha.tgconnector.domain.source.Source;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySourceRepositoryTest {

    private final InMemorySourceRepository repository = new InMemorySourceRepository();

    @Test
    void rejectsSecondSourceWithSameReferenceButAllowsUpdates() {
        var first = Source.register(ChatReference.parse("@telegram"), null, Clock.systemUTC());
        repository.save(first);

        first.pause(Clock.systemUTC());
        repository.save(first);

        assertThatThrownBy(() -> repository.save(Source.register(ChatReference.parse("@telegram"), null, Clock.systemUTC())))
                .isInstanceOf(DuplicateSourceReferenceException.class);
        assertThat(repository.findAll()).containsExactly(first);
    }
}
