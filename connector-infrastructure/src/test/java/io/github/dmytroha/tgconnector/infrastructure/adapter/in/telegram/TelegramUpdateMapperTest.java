package io.github.dmytroha.tgconnector.infrastructure.adapter.in.telegram;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramUpdateMapperTest {

    private final TelegramUpdateMapper mapper = new TelegramUpdateMapper();

    @Test
    void mapsChannelPost() {
        var chat = new Chat(-1001234567890L, "channel");
        chat.setTitle("My Channel");
        var post = new Message();
        post.setMessageId(42);
        post.setChat(chat);
        post.setDate(1_767_225_600);
        post.setText("Hello channel");
        var update = new Update();
        update.setChannelPost(post);

        var command = mapper.toCommand(update).orElseThrow();

        assertThat(command.chatId()).isEqualTo(-1001234567890L);
        assertThat(command.chatTitle()).isEqualTo("My Channel");
        assertThat(command.message().messageId().value()).isEqualTo(42);
        assertThat(command.message().content().text()).isEqualTo("Hello channel");
        assertThat(command.message().author().displayName()).isEqualTo("My Channel");
        assertThat(command.message().postedAt()).isEqualTo(Instant.ofEpochSecond(1_767_225_600));
    }

    @Test
    void ignoresUpdatesWithoutContent() {
        assertThat(mapper.toCommand(new Update())).isEmpty();
    }
}
