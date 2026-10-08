package io.github.dmytroha.tgconnector.infrastructure.adapter.in.web;

import io.github.dmytroha.tgconnector.application.dto.MessageView;
import io.github.dmytroha.tgconnector.application.dto.SourceView;
import io.github.dmytroha.tgconnector.application.port.in.ManageSourceUseCase;
import io.github.dmytroha.tgconnector.application.port.in.PollChannelsUseCase;
import io.github.dmytroha.tgconnector.application.port.in.PollChannelsUseCase.PollReport;
import io.github.dmytroha.tgconnector.application.port.in.QueryMessagesUseCase;
import io.github.dmytroha.tgconnector.application.port.in.QuerySourcesUseCase;
import io.github.dmytroha.tgconnector.application.port.in.RegisterSourceUseCase;
import io.github.dmytroha.tgconnector.application.port.in.RegisterSourceUseCase.RegisterSourceCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * Driving adapter: REST API to manage sources and read collected messages.
 */
@RestController
@RequestMapping("/api/v1/sources")
class SourceController {

    record RegisterSourceRequest(@NotBlank String reference, String title) {
    }

    private final RegisterSourceUseCase registerSource;
    private final ManageSourceUseCase manageSource;
    private final QuerySourcesUseCase querySources;
    private final QueryMessagesUseCase queryMessages;
    private final PollChannelsUseCase pollChannels;

    SourceController(RegisterSourceUseCase registerSource, ManageSourceUseCase manageSource,
                     QuerySourcesUseCase querySources, QueryMessagesUseCase queryMessages,
                     PollChannelsUseCase pollChannels) {
        this.registerSource = registerSource;
        this.manageSource = manageSource;
        this.querySources = querySources;
        this.queryMessages = queryMessages;
        this.pollChannels = pollChannels;
    }

    @PostMapping
    ResponseEntity<SourceView> register(@Valid @RequestBody RegisterSourceRequest request) {
        var created = registerSource.register(new RegisterSourceCommand(request.reference(), request.title()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    List<SourceView> list() {
        return querySources.findAll();
    }

    @GetMapping("/{id}")
    SourceView get(@PathVariable UUID id) {
        return querySources.get(id);
    }

    @PostMapping("/{id}/pause")
    SourceView pause(@PathVariable UUID id) {
        return manageSource.pause(id);
    }

    @PostMapping("/{id}/resume")
    SourceView resume(@PathVariable UUID id) {
        return manageSource.resume(id);
    }

    @GetMapping("/{id}/messages")
    List<MessageView> messages(@PathVariable UUID id, @RequestParam(defaultValue = "50") int limit) {
        return queryMessages.latest(id, limit);
    }

    /**
     * Polls all active public channels right now instead of waiting for the scheduler.
     */
    @PostMapping("/poll")
    PollReport pollNow() {
        return pollChannels.pollAll();
    }
}
