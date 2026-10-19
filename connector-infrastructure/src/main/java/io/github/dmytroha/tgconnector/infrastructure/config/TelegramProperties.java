package io.github.dmytroha.tgconnector.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "connector.telegram")
public record TelegramProperties(@DefaultValue Bot bot, @DefaultValue ChannelFeed channelFeed,
                                 @DefaultValue Polling polling) {

    /**
     * Telegram Bot API (push) settings. Get a token from @BotFather.
     *
     * @param autoRegisterChats register chats automatically when the bot first sees a message from them.
     *                          Off by default: anyone can message a bot or add it to a group.
     */
    public record Bot(@DefaultValue("false") boolean enabled, String token,
                      @DefaultValue("false") boolean autoRegisterChats) {
    }

    /**
     * Public channel web preview ({@code https://t.me/s/<channel>}) settings.
     */
    public record ChannelFeed(@DefaultValue("https://t.me") URI baseUrl,
                              @DefaultValue("10s") Duration timeout,
                              @DefaultValue("Mozilla/5.0 (compatible; tg-connector/0.1)") String userAgent) {
    }

    /**
     * Public channel polling: on a schedule, plus on demand when messages are read through MCP.
     *
     * @param cron   Spring cron expression for scheduled polls ({@code -} disables them)
     * @param zone   time zone of {@code cron}
     * @param maxAge MCP reads poll first if the last poll finished longer ago than this
     */
    public record Polling(@DefaultValue("true") boolean enabled,
                          @DefaultValue("0 0 7,13,19 * * *") String cron,
                          @DefaultValue("Europe/Prague") String zone,
                          @DefaultValue("1h") Duration maxAge) {
    }
}
