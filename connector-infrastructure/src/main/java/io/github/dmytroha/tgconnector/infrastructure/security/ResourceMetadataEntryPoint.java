package io.github.dmytroha.tgconnector.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers unauthenticated /mcp and /api calls with 401 and a pointer to the protected resource metadata,
 * which is how Claude discovers that it has to sign in.
 */
class ResourceMetadataEntryPoint implements AuthenticationEntryPoint {

    private final PublicUrls urls;

    ResourceMetadataEntryPoint(PublicUrls urls) {
        this.urls = urls;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) {
        var header = new StringBuilder("Bearer resource_metadata=\"").append(urls.resourceMetadataUrl()).append('"')
                .append(", scope=\"").append(ProtectedResourceMetadataController.SCOPE).append('"');
        if (e instanceof OAuth2AuthenticationException oauth) {
            header.append(", error=\"").append(oauth.getError().getErrorCode()).append('"');
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, header.toString());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}
