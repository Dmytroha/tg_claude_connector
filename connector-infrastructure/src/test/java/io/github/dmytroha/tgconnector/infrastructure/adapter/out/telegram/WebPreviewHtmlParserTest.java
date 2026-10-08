package io.github.dmytroha.tgconnector.infrastructure.adapter.out.telegram;

import io.github.dmytroha.tgconnector.domain.message.Attachment;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebPreviewHtmlParserTest {

    @Test
    void parsesPostsInAscendingOrderAndSkipsEmptyServiceMessages() throws IOException {
        var html = new String(getClass().getResourceAsStream("/webpreview/channel.html").readAllBytes(), StandardCharsets.UTF_8);

        var messages = new WebPreviewHtmlParser().parse(html);

        assertThat(messages).extracting(m -> m.messageId().value()).containsExactly(100L, 101L);
        var latest = messages.get(1);
        assertThat(latest.messageId()).isEqualTo(new TelegramMessageId(101));
        assertThat(latest.content().text()).isEqualTo("First line\nSecond bold line");
        assertThat(latest.content().attachments())
                .containsExactly(new Attachment(Attachment.Kind.PHOTO, "https://cdn.telesco.pe/file/photo.jpg"));
        assertThat(latest.author().displayName()).isEqualTo("Telegram News");
        assertThat(latest.postedAt()).isEqualTo(Instant.parse("2026-03-01T10:15:00Z"));
    }
}
