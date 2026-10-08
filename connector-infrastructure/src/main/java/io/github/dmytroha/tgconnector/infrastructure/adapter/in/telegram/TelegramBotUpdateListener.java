package io.github.dmytroha.tgconnector.infrastructure.adapter.in.telegram;

import io.github.dmytroha.tgconnector.application.port.in.ReceiveBotMessageUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Driving adapter: receives updates from the Telegram Bot API and forwards them to the use case.
 * Works for private chats, groups and channels where the bot is an administrator.
 */
public class TelegramBotUpdateListener implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotUpdateListener.class);

    private final ReceiveBotMessageUseCase receiveBotMessage;
    private final TelegramUpdateMapper mapper = new TelegramUpdateMapper();

    public TelegramBotUpdateListener(ReceiveBotMessageUseCase receiveBotMessage) {
        this.receiveBotMessage = receiveBotMessage;
    }

    @Override
    public void consume(Update update) {
        try {
            mapper.toCommand(update).ifPresent(command -> {
                var outcome = receiveBotMessage.receive(command);
                log.debug("Update {} from chat {} -> {}", update.getUpdateId(), command.chatId(), outcome);
            });
        } catch (RuntimeException e) {
            log.error("Failed to process Telegram update {}", update.getUpdateId(), e);
        }
    }
}
