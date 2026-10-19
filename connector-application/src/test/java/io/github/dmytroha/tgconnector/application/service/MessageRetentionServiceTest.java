package io.github.dmytroha.tgconnector.application.service;

import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageRetentionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T04:30:00Z"), ZoneOffset.UTC);
    private final IngestionServiceTest.FakeMessages messages = new IngestionServiceTest.FakeMessages();
    private final MessageRetentionService service = new MessageRetentionService(messages, clock);

    @Test
    void deletesOnlyMessagesPostedBeforeTheCutoff() {
        var channel = Source.register(ChatReference.parse("@jobs"), null, clock);
        save(channel, 1, "2026-07-01T00:00:00Z");
        save(channel, 2, "2026-07-21T04:29:59Z");
        save(channel, 3, "2026-07-21T04:30:00Z");
        save(channel, 4, "2026-10-19T00:00:00Z");

        var deleted = service.purgeOlderThan(Duration.ofDays(90));

        assertThat(deleted).isEqualTo(2);
        assertThat(messages.store.keySet()).extracting(key -> key.telegramMessageId().value())
                .containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    void rejectsNonPositiveAge() {
        assertThatThrownBy(() -> service.purgeOlderThan(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private void save(Source source, long id, String postedAt) {
        messages.save(source.accept(new TelegramMessageId(id), MessageContent.text("post " + id), Author.unknown(),
                Instant.parse(postedAt), clock).orElseThrow());
    }
}
