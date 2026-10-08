package io.github.dmytroha.tgconnector.infrastructure.adapter.in.telegram;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;
import io.github.dmytroha.tgconnector.application.port.in.ReceiveBotMessageUseCase.BotMessageCommand;
import io.github.dmytroha.tgconnector.domain.message.Attachment;
import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Anti-corruption layer: translates Telegram Bot API objects into application commands.
 */
class TelegramUpdateMapper {

    Optional<BotMessageCommand> toCommand(Update update) {
        Message message = update.hasChannelPost() ? update.getChannelPost()
                : update.hasMessage() ? update.getMessage()
                : null;
        if (message == null) {
            return Optional.empty();
        }
        var attachments = attachments(message);
        var text = message.getText() != null ? message.getText() : message.getCaption();
        if ((text == null || text.isBlank()) && attachments.isEmpty()) {
            return Optional.empty(); // service messages (joins, pins, ...) are not content
        }
        var chat = message.getChat();
        var incoming = new IncomingMessage(
                new TelegramMessageId(message.getMessageId()),
                new MessageContent(text, attachments),
                author(message),
                Instant.ofEpochSecond(message.getDate()));
        return Optional.of(new BotMessageCommand(chat.getId(), chatTitle(chat), incoming));
    }

    private Author author(Message message) {
        if (message.getFrom() != null) {
            var from = message.getFrom();
            var name = from.getLastName() == null ? from.getFirstName() : from.getFirstName() + " " + from.getLastName();
            return new Author(name, from.getUserName());
        }
        var chat = message.getSenderChat() != null ? message.getSenderChat() : message.getChat();
        var name = message.getAuthorSignature() != null ? message.getAuthorSignature() : chatTitle(chat);
        return new Author(name, chat.getUserName());
    }

    private String chatTitle(Chat chat) {
        if (chat.getTitle() != null) {
            return chat.getTitle();
        }
        return chat.getUserName() != null ? "@" + chat.getUserName() : chat.getFirstName();
    }

    private List<Attachment> attachments(Message m) {
        var result = new ArrayList<Attachment>();
        if (m.getPhoto() != null && !m.getPhoto().isEmpty()) {
            m.getPhoto().stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .ifPresent(p -> result.add(new Attachment(Attachment.Kind.PHOTO, p.getFileId())));
        }
        if (m.getVideo() != null) {
            result.add(new Attachment(Attachment.Kind.VIDEO, m.getVideo().getFileId()));
        }
        if (m.getDocument() != null) {
            result.add(new Attachment(Attachment.Kind.DOCUMENT, m.getDocument().getFileId()));
        }
        if (m.getAudio() != null) {
            result.add(new Attachment(Attachment.Kind.AUDIO, m.getAudio().getFileId()));
        }
        if (m.getVoice() != null) {
            result.add(new Attachment(Attachment.Kind.VOICE, m.getVoice().getFileId()));
        }
        if (m.getSticker() != null) {
            result.add(new Attachment(Attachment.Kind.STICKER, m.getSticker().getFileId()));
        }
        return result;
    }
}
