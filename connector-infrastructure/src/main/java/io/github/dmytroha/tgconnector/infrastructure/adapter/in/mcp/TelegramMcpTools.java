package io.github.dmytroha.tgconnector.infrastructure.adapter.in.mcp;

import io.github.dmytroha.tgconnector.application.dto.MessageView;
import io.github.dmytroha.tgconnector.application.dto.SourceView;
import io.github.dmytroha.tgconnector.application.port.in.ManageSourceUseCase;
import io.github.dmytroha.tgconnector.application.port.in.PollChannelsUseCase;
import io.github.dmytroha.tgconnector.application.port.in.PollChannelsUseCase.PollReport;
import io.github.dmytroha.tgconnector.application.port.in.QueryMessagesUseCase;
import io.github.dmytroha.tgconnector.application.port.in.QuerySourcesUseCase;
import io.github.dmytroha.tgconnector.application.port.in.RegisterSourceUseCase;
import io.github.dmytroha.tgconnector.application.port.in.RegisterSourceUseCase.RegisterSourceCommand;
import io.github.dmytroha.tgconnector.application.port.in.SearchMessagesUseCase;
import io.github.dmytroha.tgconnector.application.port.in.SearchMessagesUseCase.SearchMessagesQuery;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Driving adapter: exposes the use cases as MCP tools, so Claude can read Telegram through the connector
 * (added in claude.ai as a custom connector, served over Streamable HTTP at {@code /mcp}).
 * <p>
 * Tool results are plain records serialized to JSON. Errors are thrown as exceptions with a message
 * written for the model: the MCP server returns them as tool errors Claude can react to.
 */
@Component
class TelegramMcpTools {

    private static final int DEFAULT_LIMIT = 20;

    private final QuerySourcesUseCase querySources;
    private final RegisterSourceUseCase registerSource;
    private final ManageSourceUseCase manageSource;
    private final QueryMessagesUseCase queryMessages;
    private final SearchMessagesUseCase searchMessages;
    private final PollChannelsUseCase pollChannels;

    TelegramMcpTools(QuerySourcesUseCase querySources, RegisterSourceUseCase registerSource,
                     ManageSourceUseCase manageSource, QueryMessagesUseCase queryMessages,
                     SearchMessagesUseCase searchMessages, PollChannelsUseCase pollChannels) {
        this.querySources = querySources;
        this.registerSource = registerSource;
        this.manageSource = manageSource;
        this.queryMessages = queryMessages;
        this.searchMessages = searchMessages;
        this.pollChannels = pollChannels;
    }

    record SourceInfo(String id, String reference, String title, String kind, String status,
                      Long lastReadMessageId, String link) {
    }

    record MessageInfo(String source, long messageId, Instant postedAt, String author, String text,
                       List<String> attachments, String link) {
    }

    @McpTool(name = "list_sources",
            description = """
                    List the Telegram sources the connector reads: public channels (polled every minute) \
                    and chats where the connector's bot is a member. Call this first to learn which \
                    channels exist and their references, which other tools accept as `source`.""",
            annotations = @McpAnnotations(title = "List Telegram sources", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    List<SourceInfo> listSources() {
        return querySources.findAll().stream().map(TelegramMcpTools::toInfo).toList();
    }

    @McpTool(name = "get_recent_messages",
            description = """
                    Get the latest messages of one Telegram source, newest first. Use it for questions \
                    like "what's new in @channel". Messages are only those collected since the source \
                    was added; call refresh_sources first if the user needs the very latest posts.""",
            annotations = @McpAnnotations(title = "Recent messages of a source", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    List<MessageInfo> getRecentMessages(
            @McpToolParam(description = "Source reference from list_sources, e.g. \"@durov\", or its id")
            String source,
            @McpToolParam(description = "Maximum number of messages, 1-500. Default 20.", required = false)
            Integer limit) {
        var view = resolve(source);
        return toInfos(queryMessages.latest(view.id(), limitOrDefault(limit)), Map.of(view.id(), view));
    }

    @McpTool(name = "search_messages",
            description = """
                    Search collected Telegram messages across all sources (or the given ones), newest \
                    first. Text matching is a case-insensitive substring match, not semantic search: \
                    search for a distinctive word or word stem (e.g. "выбор" matches "выборы", "выборах"). \
                    Leave `query` empty to list everything posted since a date, e.g. for a daily digest.""",
            annotations = @McpAnnotations(title = "Search Telegram messages", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    List<MessageInfo> searchMessages(
            @McpToolParam(description = "Text to look for. Optional.", required = false)
            String query,
            @McpToolParam(description = "Source references or ids to search in. Optional: all sources by default.",
                    required = false)
            List<String> sources,
            @McpToolParam(description = """
                    Only messages posted at or after this moment, as ISO-8601 in UTC: \
                    "2026-10-08" or "2026-10-08T09:00:00Z". Optional.""", required = false)
            String since,
            @McpToolParam(description = "Maximum number of messages, 1-500. Default 20.", required = false)
            Integer limit) {
        var all = querySources.findAll().stream().collect(Collectors.toMap(SourceView::id, Function.identity()));
        var ids = sources == null ? List.<UUID>of() : sources.stream().map(s -> resolve(s).id()).toList();
        var found = searchMessages.search(new SearchMessagesQuery(query, ids, parseSince(since), limitOrDefault(limit)));
        return toInfos(found, all);
    }

    @McpTool(name = "add_source",
            description = """
                    Start reading a Telegram source. For a public channel pass its username or link \
                    ("@durov", "https://t.me/durov"): the connector polls its public page, no access \
                    needed, but only posts published from now on (plus the latest ~20) are collected. \
                    For a group or private chat pass the numeric chat id; the bot must be a member.""",
            annotations = @McpAnnotations(title = "Add Telegram source", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = true))
    SourceInfo addSource(
            @McpToolParam(description = "Channel username or link, or a numeric chat id") String reference,
            @McpToolParam(description = "Human-friendly name. Optional.", required = false) String title) {
        return toInfo(registerSource.register(new RegisterSourceCommand(reference, title)));
    }

    @McpTool(name = "pause_source",
            description = "Stop collecting new messages from a source. Already collected messages stay searchable.",
            annotations = @McpAnnotations(title = "Pause source", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    SourceInfo pauseSource(@McpToolParam(description = "Source reference or id") String source) {
        return toInfo(manageSource.pause(resolve(source).id()));
    }

    @McpTool(name = "resume_source",
            description = "Resume collecting messages from a paused source.",
            annotations = @McpAnnotations(title = "Resume source", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    SourceInfo resumeSource(@McpToolParam(description = "Source reference or id") String source) {
        return toInfo(manageSource.resume(resolve(source).id()));
    }

    @McpTool(name = "refresh_sources",
            description = """
                    Fetch new posts from all active public channels right now instead of waiting for \
                    the next scheduled poll (once a minute). Chats read by the bot are always up to date.""",
            annotations = @McpAnnotations(title = "Refresh channels now", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = true))
    PollReport refreshSources() {
        return pollChannels.pollAll();
    }

    private SourceView resolve(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("`source` is required: pass a reference from list_sources");
        }
        var all = querySources.findAll();
        var byId = parseUuid(source);
        if (byId != null) {
            return all.stream().filter(s -> s.id().equals(byId)).findFirst()
                    .orElseThrow(() -> unknownSource(source));
        }
        var reference = ChatReference.parse(source).asString();
        return all.stream().filter(s -> s.reference().equals(reference)).findFirst()
                .orElseThrow(() -> unknownSource(source));
    }

    private static IllegalArgumentException unknownSource(String source) {
        return new IllegalArgumentException(
                "Unknown source '" + source + "'. Call list_sources to see available sources, or add_source to add it.");
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        var value = since.strip();
        try {
            return value.length() == 10 ? LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("`since` must be ISO-8601, e.g. 2026-10-08 or 2026-10-08T09:00:00Z");
        }
    }

    private static int limitOrDefault(Integer limit) {
        return limit == null ? DEFAULT_LIMIT : limit;
    }

    private static SourceInfo toInfo(SourceView s) {
        var kind = "POLLING".equals(s.ingestionMode()) ? "public channel" : "bot chat";
        return new SourceInfo(s.id().toString(), s.reference(), s.title(), kind, s.status(),
                s.lastReadMessageId(), channelLink(s.reference(), null));
    }

    private static List<MessageInfo> toInfos(List<MessageView> messages, Map<UUID, SourceView> sources) {
        return messages.stream().map(m -> {
            var source = sources.get(m.sourceId());
            var reference = source == null ? m.sourceId().toString() : source.reference();
            var attachments = m.attachments().stream().map(a -> a.kind() + ": " + a.reference()).toList();
            return new MessageInfo(reference, m.messageId(), m.postedAt(), m.author(), m.text(), attachments,
                    channelLink(reference, m.messageId()));
        }).toList();
    }

    /**
     * Public link to a channel or post; only public channels have one.
     */
    private static String channelLink(String reference, Long messageId) {
        if (!reference.startsWith("@")) {
            return null;
        }
        var base = "https://t.me/" + reference.substring(1);
        return messageId == null ? base : base + "/" + messageId;
    }
}
