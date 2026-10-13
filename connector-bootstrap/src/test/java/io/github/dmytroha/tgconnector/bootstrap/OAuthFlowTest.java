package io.github.dmytroha.tgconnector.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The OAuth flow exactly as a claude.ai custom connector runs it, with the GitHub sign-in replaced by a test user.
 */
@SpringBootTest(properties = {
        "connector.telegram.polling.enabled=false",
        "connector.security.mcp-client.client-id=claude",
        "connector.security.mcp-client.client-secret=test-secret"})
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class OAuthFlowTest {

    private static final String CALLBACK = "https://claude.ai/api/mcp/auth_callback";
    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
             "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    @Autowired
    MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void mcpWithoutTokenPointsToResourceMetadata() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(INITIALIZE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/mcp\", scope=\"mcp\""));
        mvc.perform(get("/api/v1/sources")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void publishesDiscoveryMetadata() throws Exception {
        mvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("http://localhost/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("http://localhost"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("mcp"));
        mvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost/oauth2/authorize"));
    }

    @Test
    void browserThatIsNotSignedInIsSentToGitHub() throws Exception {
        mvc.perform(get("/oauth2/authorize").accept(MediaType.TEXT_HTML)
                        .queryParams(authorizeParams("challenge-not-checked-yet")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.endsWith("/oauth2/authorization/github")));
    }

    @Test
    void authorizationCodeWithPkceThenMcpThenRefreshWithRotation() throws Exception {
        var verifier = UUID.randomUUID() + "-" + UUID.randomUUID();
        var authorize = mvc.perform(get("/oauth2/authorize")
                        .queryParams(authorizeParams(challenge(verifier)))
                        .with(user("Dmytroha")))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(authorize).startsWith(CALLBACK);
        var redirect = UriComponentsBuilder.fromUriString(authorize).build().getQueryParams();
        assertThat(redirect.getFirst("state")).isEqualTo("state-1");
        var code = redirect.getFirst("code");

        // wrong verifier is rejected
        mvc.perform(post("/oauth2/token").with(httpBasic("claude", "test-secret"))
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", CALLBACK).param("code_verifier", "wrong-" + verifier))
                .andExpect(status().isBadRequest());

        var verifier2 = UUID.randomUUID() + "-" + UUID.randomUUID();
        var code2 = UriComponentsBuilder.fromUriString(mvc.perform(get("/oauth2/authorize")
                        .queryParams(authorizeParams(challenge(verifier2))).with(user("Dmytroha")))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION)).build().getQueryParams().getFirst("code");
        var tokens = token(mvc.perform(post("/oauth2/token").with(httpBasic("claude", "test-secret"))
                        .param("grant_type", "authorization_code").param("code", code2)
                        .param("redirect_uri", CALLBACK).param("code_verifier", verifier2))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(tokens).containsKeys("access_token", "refresh_token");

        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("access_token"))
                        .content(INITIALIZE))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sources").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("access_token")))
                .andExpect(status().isOk());

        // client_secret_post works too, and the refresh token rotates
        var refreshed = token(mvc.perform(post("/oauth2/token")
                        .param("grant_type", "refresh_token").param("refresh_token", (String) tokens.get("refresh_token"))
                        .param("client_id", "claude").param("client_secret", "test-secret"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(refreshed.get("refresh_token")).isNotEqualTo(tokens.get("refresh_token"));
        mvc.perform(post("/oauth2/token").with(httpBasic("claude", "test-secret"))
                        .param("grant_type", "refresh_token").param("refresh_token", (String) tokens.get("refresh_token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void rejectsTokensSignedBySomeoneElse() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(INITIALIZE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.containsString("error=\"invalid_token\"")));
    }

    private static org.springframework.util.MultiValueMap<String, String> authorizeParams(String challenge) {
        var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
        params.add("response_type", "code");
        params.add("client_id", "claude");
        params.add("redirect_uri", CALLBACK);
        params.add("scope", "mcp");
        params.add("state", "state-1");
        params.add("code_challenge", challenge);
        params.add("code_challenge_method", "S256");
        params.add("resource", "http://localhost/mcp");
        return params;
    }

    private static String challenge(String verifier) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> token(String body) {
        return json.readValue(body, Map.class);
    }
}
