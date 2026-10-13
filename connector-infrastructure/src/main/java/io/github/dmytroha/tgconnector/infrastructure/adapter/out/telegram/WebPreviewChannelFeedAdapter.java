package io.github.dmytroha.tgconnector.infrastructure.adapter.out.telegram;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;
import io.github.dmytroha.tgconnector.application.port.out.ChannelFeedPort;
import io.github.dmytroha.tgconnector.domain.source.ChatReference.ChannelUsername;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import io.github.dmytroha.tgconnector.infrastructure.config.TelegramProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

/**
 * Driven adapter: reads public channels via the official web preview {@code https://t.me/s/<channel>}.
 * <p>
 * Needs no credentials, but only works for public channels with preview enabled and returns
 * ~20 posts per request. For private channels or full history implement {@link ChannelFeedPort}
 * with an MTProto client (TDLib / TDLight) and register it instead of this bean.
 */
@Component
class WebPreviewChannelFeedAdapter implements ChannelFeedPort {

    private final HttpClient http;
    private final TelegramProperties.ChannelFeed config;
    private final WebPreviewHtmlParser parser = new WebPreviewHtmlParser();

    WebPreviewChannelFeedAdapter(TelegramProperties properties) {
        this.config = properties.channelFeed();
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    @Override
    public List<IncomingMessage> fetch(ChannelUsername channel, Optional<TelegramMessageId> after) {
        var request = HttpRequest.newBuilder(uri(channel, after))
                .timeout(config.timeout())
                .header("User-Agent", config.userAgent())
                .GET()
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ChannelFeedException("HTTP " + response.statusCode() + " for " + request.uri());
            }
            return parser.parse(response.body()).stream()
                    .filter(m -> after.map(m.messageId()::isAfter).orElse(true))
                    .toList();
        } catch (IOException e) {
            throw new ChannelFeedException("Cannot fetch " + request.uri() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChannelFeedException("Interrupted while fetching " + request.uri(), e);
        }
    }

    private URI uri(ChannelUsername channel, Optional<TelegramMessageId> after) {
        var base = config.baseUrl().toString().replaceAll("/+$", "") + "/s/" + channel.value();
        return URI.create(after.map(id -> base + "?after=" + id.value()).orElse(base));
    }

    static class ChannelFeedException extends RuntimeException {
        ChannelFeedException(String message) {
            super(message);
        }

        ChannelFeedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
