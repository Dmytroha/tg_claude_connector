package io.github.dmytroha.tgconnector.application.port.in;

import io.github.dmytroha.tgconnector.application.dto.SourceView;

import java.util.UUID;

public interface ManageSourceUseCase {

    SourceView pause(UUID sourceId);

    SourceView resume(UUID sourceId);
}
