package io.github.dmytroha.tgconnector.application.port.in;

import io.github.dmytroha.tgconnector.application.dto.SourceView;

import java.util.List;
import java.util.UUID;

public interface QuerySourcesUseCase {

    List<SourceView> findAll();

    SourceView get(UUID sourceId);
}
