package io.github.dmytroha.tgconnector.infrastructure.security;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Public URLs of the connector: from configuration, or from the current request (behind a proxy this relies on
 * {@code X-Forwarded-*} headers, see {@code server.forward-headers-strategy}).
 */
public class PublicUrls {

    public static final String MCP_PATH = "/mcp";
    public static final String RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource";

    private final String configuredBaseUrl;

    public PublicUrls(SecurityProperties properties) {
        this.configuredBaseUrl = properties.normalizedPublicUrl();
    }

    public boolean configured() {
        return configuredBaseUrl != null;
    }

    public String baseUrl() {
        return configuredBaseUrl != null ? configuredBaseUrl
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString().replaceAll("/+$", "");
    }

    /** OAuth issuer: the connector itself. */
    public String issuer() {
        return baseUrl();
    }

    /** The MCP endpoint URL: the "resource" Claude asks tokens for and their audience. */
    public String mcpResource() {
        return baseUrl() + MCP_PATH;
    }

    public String resourceMetadataUrl() {
        return baseUrl() + RESOURCE_METADATA_PATH + MCP_PATH;
    }
}
