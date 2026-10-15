package io.github.dmytroha.tgconnector.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * @param publicUrl public base URL, e.g. {@code https://connector.up.railway.app} (a bare host gets {@code https://}).
 *                  When set, it is the token issuer and tokens are checked against it; when empty, it is taken
 *                  from each request (fine for local runs).
 */
@ConfigurationProperties(prefix = "connector.security")
public record SecurityProperties(String publicUrl, @DefaultValue GitHub github, @DefaultValue McpClient mcpClient,
                                 @DefaultValue Tokens tokens) {

    /**
     * GitHub OAuth App used to sign users in.
     *
     * @param allowedLogins GitHub logins allowed to sign in (case-insensitive). Empty — nobody can sign in.
     */
    public record GitHub(String clientId, String clientSecret, @DefaultValue List<String> allowedLogins) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    /**
     * The OAuth client Claude uses: entered as "OAuth Client ID" / "OAuth Client Secret" when adding the connector.
     */
    public record McpClient(@DefaultValue("claude") String clientId, String clientSecret,
                            @DefaultValue("https://claude.ai/api/mcp/auth_callback") List<String> redirectUris) {
    }

    public record Tokens(@DefaultValue("1h") Duration accessTokenTtl, @DefaultValue("30d") Duration refreshTokenTtl) {
    }

    /**
     * {@link #publicUrl()} normalized: scheme added, trailing slash removed; {@code null} when not configured.
     */
    public String normalizedPublicUrl() {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        var url = publicUrl.strip().replaceAll("/+$", "");
        return url.contains("://") ? url : "https://" + url;
    }
}
