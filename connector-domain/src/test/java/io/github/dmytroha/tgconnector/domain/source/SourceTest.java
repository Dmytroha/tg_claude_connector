package io.github.dmytroha.tgconnector.domain.source;

import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.message.MessageReceived;
import io.github.dmytroha.tgconnector.domain.shared.DomainException;
import io.github.dmytroha.tgconnector.domain.source.SourceEvents.SourcePaused;
import io.github.dmytroha.tgconnector.domain.source.SourceEvents.SourceRegistered;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void registersPublicChannelForPolling() {
        var source = Source.register(ChatReference.parse("https://t.me/Telegram"), null, clock);

        assertThat(source.reference()).isEqualTo(new ChatReference.ChannelUsername("telegram"));
        assertThat(source.ingestionMode()).isEqualTo(IngestionMode.POLLING);
        assertThat(source.title()).isEqualTo("@telegram");
        assertThat(source.pullEvents()).singleElement().isInstanceOf(SourceRegistered.class);
    }

    @Test
    void numericReferenceIsBotChat() {
        assertThat(ChatReference.parse("-1001234567890").ingestionMode()).isEqualTo(IngestionMode.BOT_UPDATES);
    }

    @Test
    void rejectsInvalidUsername() {
        assertThatThrownBy(() -> ChatReference.parse("@a b")).isInstanceOf(DomainException.class);
    }

    @Test
    void acceptsEachMessageOnlyOnceAndAdvancesCursor() {
        var source = Source.register(ChatReference.parse("@telegram"), "Telegram", clock);

        var first = source.accept(new TelegramMessageId(10), MessageContent.text("hi"), Author.unknown(), clock.instant(), clock);
        var duplicate = source.accept(new TelegramMessageId(10), MessageContent.text("hi"), Author.unknown(), clock.instant(), clock);
        var older = source.accept(new TelegramMessageId(5), MessageContent.text("old"), Author.unknown(), clock.instant(), clock);

        assertThat(first).isPresent();
        assertThat(first.get().pullEvents()).singleElement().isInstanceOf(MessageReceived.class);
        assertThat(duplicate).isEmpty();
        assertThat(older).isEmpty();
        assertThat(source.lastReadMessageId()).contains(new TelegramMessageId(10));
    }

    @Test
    void pausedSourceIgnoresMessages() {
        var source = Source.register(ChatReference.parse("@telegram"), null, clock);
        source.pullEvents();

        source.pause(clock);

        assertThat(source.pullEvents()).singleElement().isInstanceOf(SourcePaused.class);
        assertThat(source.accept(new TelegramMessageId(1), MessageContent.text("x"), null, clock.instant(), clock)).isEmpty();
    }
}
