package io.github.dmytroha.tgconnector.application.port.out;

import io.github.dmytroha.tgconnector.application.dto.IncomingMessage;
import io.github.dmytroha.tgconnector.domain.source.ChatReference.ChannelUsername;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;

import java.util.List;
import java.util.Optional;

/**
 * Reads posts of a public Telegram channel. Implementations: public web preview, MTProto (TDLib) client, ...
 */
public interface ChannelFeedPort {

    /**
     * @param after only return messages newer than this id (empty — return the latest page)
     * @return messages ordered by id ascending
     */
    List<IncomingMessage> fetch(ChannelUsername channel, Optional<TelegramMessageId> after);
}
