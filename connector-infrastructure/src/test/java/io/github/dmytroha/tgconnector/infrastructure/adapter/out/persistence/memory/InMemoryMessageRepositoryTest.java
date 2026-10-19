package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence.memory;

import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMessageRepositoryTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryMessageRepository repository = new InMemoryMessageRepository();

    @Test
    void deletesMessagesPostedBeforeCutoff() {
        var channel = Source.register(ChatReference.parse("@jobs"), null, clock);
        save(channel, 1, "2026-07-01T00:00:00Z");
        save(channel, 2, "2026-10-18T00:00:00Z");

        var deleted = repository.deletePostedBefore(Instant.parse("2026-07-21T00:00:00Z"));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findLatestBySource(channel.id(), 10)).extracting(m -> m.id().telegramMessageId().value())
                .containsExactly(2L);
    }

    private void save(Source source, long id, String postedAt) {
        repository.save(source.accept(new TelegramMessageId(id), MessageContent.text("post " + id), null,
                Instant.parse(postedAt), clock).orElseThrow());
    }
}
