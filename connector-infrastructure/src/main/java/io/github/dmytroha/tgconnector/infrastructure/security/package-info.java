/**
 * Security: the connector is its own OAuth 2.1 authorization server for Claude (the only registered client),
 * users sign in with GitHub (an allowlist of logins), and {@code /mcp} and {@code /api} accept only JWTs it issued.
 * <p>
 * Flow, as a claude.ai custom connector sees it:
 * <ol>
 *     <li>{@code /mcp} answers 401 with {@code WWW-Authenticate: Bearer resource_metadata="…"};</li>
 *     <li>Claude reads the protected resource metadata (RFC 9728) and the authorization server metadata (RFC 8414);</li>
 *     <li>the user is sent to {@code /oauth2/authorize}, signs in with GitHub, and Claude gets a code (PKCE S256);</li>
 *     <li>Claude exchanges it at {@code /oauth2/token} for an access token (JWT, audience = the MCP URL) and a refresh token.</li>
 * </ol>
 */
package io.github.dmytroha.tgconnector.infrastructure.security;
