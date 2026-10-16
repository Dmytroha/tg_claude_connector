package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Messages stored before V3 get their hashtags from the Java migration V4.
 */
@EnabledIf("databaseAvailable")
class HashtagBackfillMigrationTest {

    private static PostgreSQLContainer container;

    static boolean databaseAvailable() {
        return System.getenv("TEST_DATABASE_URL") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void backfillsHashtagsOfExistingMessages() {
        var dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).target("2").load().migrate();
        var jdbc = JdbcClient.create(dataSource);
        var sourceId = UUID.randomUUID();
        jdbc.sql("INSERT INTO sources VALUES (?, '@jobs', 'Jobs', 'ACTIVE', 2, now(), 1)").param(sourceId).update();
        jdbc.sql("""
                INSERT INTO messages (source_id, message_id, text, author_name, posted_at, received_at)
                VALUES (?, 1, 'Склад #Склад #логистика', 'a', now(), now()), (?, 2, 'Без тегов', 'a', now(), now())""")
                .param(sourceId).param(sourceId).update();

        Flyway.configure().dataSource(dataSource).load().migrate();

        var tags = jdbc.sql("SELECT hashtags FROM messages WHERE source_id = ? ORDER BY message_id")
                .param(sourceId)
                .query((rs, n) -> Arrays.asList((String[]) rs.getArray(1).getArray()))
                .list();
        assertThat(tags).containsExactly(java.util.List.of("склад", "логистика"), java.util.List.of());
    }

    private static DriverManagerDataSource dataSource() {
        if (System.getenv("TEST_DATABASE_URL") != null) {
            return new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                    System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        }
        if (container == null) {
            container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
        }
        return new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
