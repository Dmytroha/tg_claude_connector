package io.github.dmytroha.tgconnector.infrastructure.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth 2.0 Protected Resource Metadata (RFC 9728): tells Claude which authorization server issues tokens for /mcp.
 */
@RestController
class ProtectedResourceMetadataController {

    static final String SCOPE = "mcp";

    private final PublicUrls urls;

    ProtectedResourceMetadataController(PublicUrls urls) {
        this.urls = urls;
    }

    @GetMapping({PublicUrls.RESOURCE_METADATA_PATH, PublicUrls.RESOURCE_METADATA_PATH + PublicUrls.MCP_PATH})
    Map<String, Object> metadata() {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("resource", urls.mcpResource());
        metadata.put("authorization_servers", List.of(urls.issuer()));
        metadata.put("scopes_supported", List.of(SCOPE));
        metadata.put("bearer_methods_supported", List.of("header"));
        metadata.put("resource_name", "Telegram Connector");
        return metadata;
    }
}
