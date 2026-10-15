package io.github.dmytroha.tgconnector.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Three filter chains, most specific first:
 * <ol>
 *     <li>OAuth authorization server endpoints ({@code /oauth2/*}, its {@code /.well-known} metadata);</li>
 *     <li>the protected API: {@code /mcp} and {@code /api/**} require a JWT issued by this server;</li>
 *     <li>everything else: GitHub sign-in, public metadata and health; the rest is denied.</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    static final String GITHUB = "github";
    private static final String GITHUB_LOGIN_PATH = "/oauth2/authorization/" + GITHUB;

    @Bean
    PublicUrls publicUrls(SecurityProperties properties) {
        return new PublicUrls(properties);
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(server -> http.securityMatcher(server.getEndpointsMatcher()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // a browser that is not signed in yet goes to GitHub; afterwards the saved request resumes
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(GITHUB_LOGIN_PATH),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain protectedApiChain(HttpSecurity http, PublicUrls urls) throws Exception {
        var entryPoint = new ResourceMetadataEntryPoint(urls);
        http.securityMatcher(PublicUrls.MCP_PATH, PublicUrls.MCP_PATH + "/**", "/api/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/.well-known/**", "/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        var github = properties.github();
        if (github.configured()) {
            var userService = new AllowedGitHubUserService(new DefaultOAuth2UserService(), github.allowedLogins());
            http.oauth2Login(login -> login
                    .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                    .failureHandler((request, response, e) -> response.sendError(403, e.getMessage())));
            if (github.allowedLogins().stream().allMatch(String::isBlank)) {
                log.warn("connector.security.github.allowed-logins is empty: nobody can sign in");
            }
        } else {
            log.warn("GitHub sign-in is not configured (GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET): "
                    + "/mcp and /api stay locked and Claude cannot be authorized");
        }
        return http.build();
    }

    /**
     * GitHub sign-in. Without credentials the app still starts and everything stays locked.
     */
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(SecurityProperties properties) {
        var github = properties.github();
        if (!github.configured()) {
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GITHUB.getBuilder(GITHUB)
                .clientId(github.clientId())
                .clientSecret(github.clientSecret())
                .userNameAttributeName("login")
                .build());
    }

    /**
     * Claude is the only client. With a secret it is a confidential client and gets refresh tokens;
     * without one it is a public PKCE client and has to sign in again when the access token expires.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository(SecurityProperties properties) {
        var client = properties.mcpClient();
        var tokens = properties.tokens();
        var hasSecret = client.clientSecret() != null && !client.clientSecret().isBlank();
        var builder = RegisteredClient.withId(UUID.nameUUIDFromBytes(client.clientId().getBytes()).toString())
                .clientId(client.clientId())
                .clientName("Claude")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(client.redirectUris()))
                .scope(ProtectedResourceMetadataController.SCOPE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(tokens.accessTokenTtl())
                        .refreshTokenTimeToLive(tokens.refreshTokenTtl())
                        .reuseRefreshTokens(false) // rotate refresh tokens
                        .build());
        if (hasSecret) {
            builder.clientSecret("{noop}" + client.clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        return new InMemoryRegisteredClientRepository(builder.build());
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(SecurityProperties properties) {
        var builder = AuthorizationServerSettings.builder();
        var publicUrl = properties.normalizedPublicUrl();
        if (publicUrl != null) {
            builder.issuer(publicUrl);
        }
        return builder.build();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeys keys) {
        return (selector, context) -> selector.select(new JWKSet(keys.current()));
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Accepts only tokens this server issued for its own MCP endpoint (issuer and audience checked per request).
     */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, PublicUrls urls) {
        var decoder = (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        OAuth2TokenValidator<Jwt> issuerAndAudience = jwt -> {
            var issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            if (!Objects.equals(issuer, urls.issuer())) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected issuer", null));
            }
            if (jwt.getAudience() == null || !jwt.getAudience().contains(urls.mcpResource())) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected audience", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuerAndAudience));
        return decoder;
    }

    /** Access tokens are issued for the MCP endpoint (RFC 8707 resource indicator). */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accessTokenAudience(PublicUrls urls) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                // a mutable ArrayList: claims are stored as JSON by JdbcOAuth2AuthorizationService, whose type
                // allowlist rejects List.of(...) implementations when the authorization is read back on refresh
                context.getClaims().audience(new ArrayList<>(List.of(urls.mcpResource())));
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "connector", name = "persistence", havingValue = "postgres", matchIfMissing = true)
    static class Durable {

        @Bean
        SigningKeys signingKeys(JdbcClient jdbc) {
            return new SigningKeys.Jdbc(jdbc);
        }

        @Bean
        OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
            return new JdbcOAuth2AuthorizationService(jdbc, clients);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "connector", name = "persistence", havingValue = "memory")
    static class NonDurable {

        @Bean
        SigningKeys signingKeys() {
            return new SigningKeys.InMemory();
        }

        @Bean
        OAuth2AuthorizationService authorizationService() {
            return new InMemoryOAuth2AuthorizationService();
        }
    }
}
