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
    void parsesPostsInAscendingOrderAndSkipsEmptyOrBrokenPosts() throws IOException {
        var messages = new WebPreviewHtmlParser().parse(fixture());

        assertThat(messages).extracting(m -> m.messageId().value()).containsExactly(100L, 101L, 102L);
        var latest = messages.get(1); // id 101
        assertThat(latest.messageId()).isEqualTo(new TelegramMessageId(101));
        assertThat(latest.content().text()).isEqualTo("First line\nSecond bold line");
        assertThat(latest.content().attachments())
                .containsExactly(new Attachment(Attachment.Kind.PHOTO, "https://cdn.telesco.pe/file/photo.jpg"));
        assertThat(latest.author().displayName()).isEqualTo("Telegram News");
        assertThat(latest.postedAt()).isEqualTo(Instant.parse("2026-03-01T10:15:00Z"));
    }

    @Test
    void ignoresQuotedReplyPreviewAndBlankDocumentTitles() throws IOException {
        var reply = new WebPreviewHtmlParser().parse(fixture()).getLast();

        assertThat(reply.messageId().value()).isEqualTo(102L);
        assertThat(reply.content().text()).isEqualTo("Reply text");
        assertThat(reply.content().attachments()).isEmpty();
    }

    private String fixture() throws IOException {
        try (var in = getClass().getResourceAsStream("/webpreview/channel.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
