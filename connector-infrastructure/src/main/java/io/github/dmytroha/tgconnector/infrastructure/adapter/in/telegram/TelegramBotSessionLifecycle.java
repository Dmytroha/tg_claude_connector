package io.github.dmytroha.tgconnector.infrastructure.adapter.in.telegram;

import io.github.dmytroha.tgconnector.application.port.in.ReceiveBotMessageUseCase;
import io.github.dmytroha.tgconnector.infrastructure.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Starts/stops the long-polling bot session together with the Spring context.
 */
@Component
@ConditionalOnProperty(prefix = "connector.telegram.bot", name = "enabled", havingValue = "true")
class TelegramBotSessionLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotSessionLifecycle.class);

    private final String token;
    private final TelegramBotUpdateListener listener;
    private TelegramBotsLongPollingApplication application;

    TelegramBotSessionLifecycle(TelegramProperties properties, ReceiveBotMessageUseCase receiveBotMessage) {
        var bot = properties.bot();
        if (bot.token() == null || bot.token().isBlank()) {
            throw new IllegalStateException("connector.telegram.bot.token must be set when the bot is enabled");
        }
        this.token = bot.token();
        this.listener = new TelegramBotUpdateListener(receiveBotMessage);
    }

    @Override
    public void start() {
        try {
            application = new TelegramBotsLongPollingApplication();
            application.registerBot(token, listener);
            log.info("Telegram bot long polling started");
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Cannot start Telegram bot session", e);
        }
    }

    @Override
    public void stop() {
        if (application == null) {
            return;
        }
        try {
            application.close();
            log.info("Telegram bot long polling stopped");
        } catch (Exception e) {
            log.warn("Error while stopping Telegram bot session", e);
        } finally {
            application = null;
        }
    }

    @Override
    public boolean isRunning() {
        return application != null && application.isRunning();
    }
}
