package io.github.dmytroha.tgconnector.application.port.in;

import io.github.dmytroha.tgconnector.application.dto.SourceView;

public interface RegisterSourceUseCase {

    /**
     * @param reference public channel ({@code @name}, {@code https://t.me/name}) or numeric bot chat id
     */
    record RegisterSourceCommand(String reference, String title) {
    }

    SourceView register(RegisterSourceCommand command);
}
