package io.github.dmytroha.tgconnector.infrastructure.security;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signing keys and issued tokens in a real PostgreSQL: encryption at rest, migration of plaintext rows, key rotation.
 */
@EnabledIf("databaseAvailable")
class SecretStoragePostgresTest {

    private static PostgreSQLContainer container;
    private static JdbcTemplate jdbcTemplate;
    private static JdbcClient jdbc;

    private final SecretCipher cipher = SecretCipher.fromSecret("0123456789abcdef0123456789abcdef-test");
    private final RegisteredClient client = RegisteredClient.withId("claude-id").clientId("claude")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://claude.ai/api/mcp/auth_callback").build();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-21T08:00:00Z"));

    static boolean databaseAvailable() {
        return System.getenv("TEST_DATABASE_URL") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @BeforeAll
    static void startDatabase() {
        String url = System.getenv("TEST_DATABASE_URL");
        String user = System.getenv("TEST_DATABASE_USER");
        String password = System.getenv("TEST_DATABASE_PASSWORD");
        if (url == null) {
            container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbc = JdbcClient.create(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE oauth2_authorization, oauth2_signing_key").update();
    }

    @Test
    void storesTokensEncryptedAndFindsThem() {
        var service = authorizations(cipher);

        service.save(authorization("access-1", "refresh-1"));

        assertThat(jdbc.sql("SELECT access_token_value || ' ' || refresh_token_value FROM oauth2_authorization")
                .query(String.class).single()).doesNotContain("access-1", "refresh-1").contains(SecretCipher.PREFIX);
        var found = service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN);
        assertThat(found).isNotNull();
        assertThat(found.getRefreshToken().getToken().getTokenValue()).isEqualTo("refresh-1");
        assertThat(found.getAccessToken().getToken().getTokenValue()).isEqualTo("access-1");
        assertThat(service.findByToken("access-1", null)).isNotNull();
        assertThat(service.findByToken("unknown", OAuth2TokenType.REFRESH_TOKEN)).isNull();
    }

    @Test
    void encryptsTokensStoredBeforeEncryptionWasEnabled() {
        authorizations(SecretCipher.disabled()).save(authorization("access-1", "refresh-1"));
        assertThat(jdbc.sql("SELECT refresh_token_value FROM oauth2_authorization").query(String.class).single())
                .isEqualTo("refresh-1");

        var service = authorizations(cipher);
        assertThat(service.encryptStoredTokens()).isEqualTo(2);

        assertThat(jdbc.sql("SELECT refresh_token_value FROM oauth2_authorization").query(String.class).single())
                .startsWith(SecretCipher.PREFIX);
        assertThat(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN)).isNotNull();
        assertThat(service.encryptStoredTokens()).isZero();
    }

    @Test
    void encryptsTheSigningKeyAndRotatesIt() {
        var plaintextKey = SigningKeys.generate();
        jdbc.sql("INSERT INTO oauth2_signing_key (id, jwk, created_at) VALUES ('current', :jwk, :at)")
                .param("jwk", plaintextKey.toJSONString())
                .param("at", java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(10))))
                .update();
        var keys = new SigningKeys.Jdbc(jdbc, cipher, clock, Duration.ofDays(30), Duration.ofHours(1));

        assertThat(keys.current().getKeyID()).as("the existing key is kept").isEqualTo(plaintextKey.getKeyID());
        assertThat(jdbc.sql("SELECT jwk FROM oauth2_signing_key").query(String.class).single())
                .as("and encrypted in place").startsWith(SecretCipher.PREFIX);

        clock.advance(Duration.ofDays(20));
        var rotated = keys.current();
        assertThat(rotated.getKeyID()).isNotEqualTo(plaintextKey.getKeyID());
        assertThat(keys.verificationKeys()).extracting(k -> k.getKeyID())
                .as("the previous key still verifies tokens it signed in the last hour")
                .containsExactly(rotated.getKeyID(), plaintextKey.getKeyID());

        clock.advance(Duration.ofMinutes(61));
        assertThat(keys.verificationKeys()).extracting(k -> k.getKeyID()).containsExactly(rotated.getKeyID());

        clock.advance(Duration.ofDays(30));
        var third = keys.current();
        assertThat(third.getKeyID()).isNotIn(rotated.getKeyID(), plaintextKey.getKeyID());
        assertThat(jdbc.sql("SELECT count(*) FROM oauth2_signing_key").query(Long.class).single())
                .as("only the two newest keys are kept").isEqualTo(2);
        assertThat(new SigningKeys.Jdbc(jdbc, cipher, clock, Duration.ofDays(30), Duration.ofHours(1)).current()
                .getKeyID()).as("a restart picks up the same key").isEqualTo(third.getKeyID());
    }

    private EncryptingAuthorizationService authorizations(SecretCipher cipher) {
        return new EncryptingAuthorizationService(jdbcTemplate, new InMemoryRegisteredClientRepository(client), cipher);
    }

    private OAuth2Authorization authorization(String access, String refresh) {
        var now = Instant.now();
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName("Dmytroha")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, access, now, now.plusSeconds(3600)))
                .refreshToken(new OAuth2RefreshToken(refresh, now, now.plusSeconds(86400)))
                .build();
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
