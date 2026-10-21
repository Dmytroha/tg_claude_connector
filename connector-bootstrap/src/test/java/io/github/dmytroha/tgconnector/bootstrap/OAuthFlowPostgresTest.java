package io.github.dmytroha.tgconnector.bootstrap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same OAuth flow with the durable stores (JDBC authorizations, signing key in PostgreSQL): authorizations are
 * serialized to JSON there, which the in-memory stores never exercise. Uses {@code TEST_DATABASE_URL}
 * (+ {@code TEST_DATABASE_USER}, {@code TEST_DATABASE_PASSWORD}) if set, otherwise Testcontainers.
 */
@ActiveProfiles(profiles = {}, inheritProfiles = false)
@EnabledIf("databaseAvailable")
class OAuthFlowPostgresTest extends OAuthFlowTest {

    private static PostgreSQLContainer container;

    static boolean databaseAvailable() {
        return System.getenv("TEST_DATABASE_URL") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("connector.security.encryption-key", () -> "0123456789abcdef0123456789abcdef-test");
        if (System.getenv("TEST_DATABASE_URL") != null) {
            registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
            return;
        }
        if (container == null) {
            container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    @Test
    void storesTheSigningKeyAndTokensEncrypted() throws Exception {
        authorizationCodeWithPkceThenMcpThenRefreshWithRotation();

        assertThat(jdbc.sql("SELECT jwk FROM oauth2_signing_key").query(String.class).list())
                .isNotEmpty().allMatch(jwk -> jwk.startsWith("enc:v1:"));
        assertThat(jdbc.sql("""
                        SELECT coalesce(access_token_value, '') || coalesce(refresh_token_value, '')
                               || coalesce(authorization_code_value, '') FROM oauth2_authorization""")
                .query(String.class).list())
                .isNotEmpty().noneMatch(tokens -> tokens.contains("eyJ"));
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
