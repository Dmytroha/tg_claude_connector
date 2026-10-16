package io.github.dmytroha.tgconnector.infrastructure.adapter.in.mcp;

import io.github.dmytroha.tgconnector.application.dto.MessageView.AttachmentView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMcpToolsMediaTest {

    private final List<AttachmentView> album = List.of(
            new AttachmentView("VIDEO", "https://cdn/1.mp4?token=a"),
            new AttachmentView("VIDEO", "https://cdn/2.mp4?token=b"),
            new AttachmentView("PHOTO", "https://cdn/p.jpg"));

    @Test
    void summarizesMediaByDefault() {
        assertThat(TelegramMcpTools.media(album, false)).containsExactly("VIDEO ×2", "PHOTO");
        assertThat(TelegramMcpTools.media(List.of(), false)).isEmpty();
    }

    @Test
    void listsUrlsOnRequest() {
        assertThat(TelegramMcpTools.media(album, true)).containsExactly(
                "VIDEO: https://cdn/1.mp4?token=a", "VIDEO: https://cdn/2.mp4?token=b", "PHOTO: https://cdn/p.jpg");
    }
}
