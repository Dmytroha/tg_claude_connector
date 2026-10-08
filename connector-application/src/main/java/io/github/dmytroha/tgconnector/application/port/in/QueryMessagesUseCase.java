package io.github.dmytroha.tgconnector.application.port.in;

import io.github.dmytroha.tgconnector.application.dto.MessageView;

import java.util.List;
import java.util.UUID;

public interface QueryMessagesUseCase {

    List<MessageView> latest(UUID sourceId, int limit);
}
