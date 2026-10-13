package io.github.dmytroha.tgconnector.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Talks to the MCP endpoint over Streamable HTTP the way a claude.ai custom connector does:
 * initialize, then list and call tools within the returned session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "connector.telegram.polling.enabled=false")
@ActiveProfiles("memory")
class McpServerTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void exposesTelegramToolsOverStreamableHttp() throws Exception {
        var init = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                 "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""");
        assertThat(init.statusCode()).isEqualTo(200);
        assertThat(init.body()).contains("\"name\":\"telegram-connector\"");
        var session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();

        post(session, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}""");

        var tools = post(session, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}""");
        assertThat(tools.body()).contains("list_sources", "get_recent_messages", "search_messages",
                "add_source", "pause_source", "resume_source", "refresh_sources");

        var added = post(session, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"add_source","arguments":{"reference":"https://t.me/telegram"}}}""");
        assertThat(added.body()).contains("@telegram", "public channel").doesNotContain("\"isError\":true");

        var unknown = post(session, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call",
                 "params":{"name":"get_recent_messages","arguments":{"source":"@missing"}}}""");
        assertThat(unknown.body()).contains("\"isError\":true", "Unknown source");
    }

    private HttpResponse<String> post(String session, String body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session != null) {
            request.header("Mcp-Session-Id", session);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
