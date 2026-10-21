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
                                 @DefaultValue Tokens tokens, String encryptionKey,
                                 @DefaultValue SigningKey signingKey) {

    /**
     * GitHub OAuth App used to sign users in.
     *
     * @param allowedIds    numeric GitHub user ids allowed to sign in. When set, logins are not checked.
     * @param allowedLogins GitHub logins allowed to sign in (case-insensitive), used only without ids.
     *                      Both empty — nobody can sign in.
     */
    public record GitHub(String clientId, String clientSecret, @DefaultValue List<String> allowedLogins,
                         @DefaultValue List<String> allowedIds) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        /** {@link #allowedIds()} parsed; blanks dropped. */
        public List<Long> parsedAllowedIds() {
            return allowedIds.stream().map(String::strip).filter(s -> !s.isEmpty()).map(id -> {
                try {
                    return Long.parseLong(id);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("connector.security.github.allowed-ids must be numeric "
                            + "GitHub user ids, got '" + id + "'");
                }
            }).toList();
        }

        public boolean hasAllowedLogins() {
            return allowedLogins.stream().anyMatch(s -> !s.isBlank());
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
     * @param rotationPeriod how long a token signing key is used before a new one replaces it
     */
    public record SigningKey(@DefaultValue("30d") Duration rotationPeriod) {
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
