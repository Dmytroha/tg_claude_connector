package io.github.dmytroha.tgconnector.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

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

    @Autowired
    JwtEncoder jwtEncoder;

    private String accessToken;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void requiresAToken() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).get().asString()
                .contains("resource_metadata=\"http://localhost:" + port + "/.well-known/oauth-protected-resource/mcp\"");
    }

    @Test
    void exposesTelegramToolsOverStreamableHttp() throws Exception {
        var base = "http://localhost:" + port;
        var now = Instant.now();
        accessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().issuer(base).subject("Dmytroha").audience(List.of(base + "/mcp"))
                        .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("scope", "mcp").build())).getTokenValue();

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
                "add_source", "pause_source", "resume_source", "refresh_sources", "list_hashtags");

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
        request.header("Authorization", "Bearer " + accessToken);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
