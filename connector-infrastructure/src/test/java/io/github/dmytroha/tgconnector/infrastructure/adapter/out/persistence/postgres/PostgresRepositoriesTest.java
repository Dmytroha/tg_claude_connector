package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence.postgres;

import io.github.dmytroha.tgconnector.domain.message.Attachment;
import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.message.MessageCriteria;
import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.ConcurrentSourceModificationException;
import io.github.dmytroha.tgconnector.domain.source.DuplicateSourceReferenceException;
import io.github.dmytroha.tgconnector.domain.source.IngestionMode;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceStatus;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL: the one in {@code TEST_DATABASE_URL} (+ {@code TEST_DATABASE_USER},
 * {@code TEST_DATABASE_PASSWORD}) if set, otherwise a Testcontainers container. Skipped when neither is available.
 */
@EnabledIf("databaseAvailable")
class PostgresRepositoriesTest {

    private static PostgreSQLContainer container;
    private static JdbcClient jdbc;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC);
    private PostgresSourceRepository sources;
    private PostgresMessageRepository messages;

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
        jdbc = JdbcClient.create(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE messages, sources").update();
        sources = new PostgresSourceRepository(jdbc);
        messages = new PostgresMessageRepository(jdbc);
    }

    @Test
    void savesAndRestoresSources() {
        var source = sources.save(Source.register(ChatReference.parse("@telegram"), "Telegram News", clock));
        source.accept(new TelegramMessageId(7), MessageContent.text("hi"), null, clock.instant(), clock);
        sources.save(source);

        var loaded = sources.findByReference(ChatReference.parse("https://t.me/telegram")).orElseThrow();

        assertThat(loaded.id()).isEqualTo(source.id());
        assertThat(loaded.title()).isEqualTo("Telegram News");
        assertThat(loaded.status()).isEqualTo(SourceStatus.ACTIVE);
        assertThat(loaded.lastReadMessageId()).contains(new TelegramMessageId(7));
        assertThat(loaded.version()).isEqualTo(2);
        assertThat(sources.findActiveByIngestionMode(IngestionMode.POLLING)).hasSize(1);
        assertThat(sources.findActiveByIngestionMode(IngestionMode.BOT_UPDATES)).isEmpty();
    }

    @Test
    void rejectsDuplicateReference() {
        sources.save(Source.register(ChatReference.parse("@telegram"), null, clock));

        assertThatThrownBy(() -> sources.save(Source.register(ChatReference.parse("@telegram"), null, clock)))
                .isInstanceOf(DuplicateSourceReferenceException.class);
    }

    @Test
    void detectsConcurrentModification() {
        var registered = sources.save(Source.register(ChatReference.parse("@telegram"), null, clock));
        var first = sources.findById(registered.id()).orElseThrow();
        var second = sources.findById(registered.id()).orElseThrow();

        first.pause(clock);
        sources.save(first);
        second.rename("stale");

        assertThatThrownBy(() -> sources.save(second)).isInstanceOf(ConcurrentSourceModificationException.class);
        assertThat(sources.findById(registered.id()).orElseThrow().status()).isEqualTo(SourceStatus.PAUSED);
    }

    @Test
    void storesMessagesOnceAndSearchesThem() {
        var channel = sources.save(Source.register(ChatReference.parse("@telegram"), null, clock));
        var other = sources.save(Source.register(ChatReference.parse("@other"), null, clock));
        var photo = new Attachment(Attachment.Kind.PHOTO, "https://cdn/p.jpg");
        var elections = channel.accept(new TelegramMessageId(1), MessageContent.text("Выборы прошли спокойно"),
                new Author("Telegram", "telegram"), Instant.parse("2026-10-08T10:00:00Z"), clock).orElseThrow();
        var release = channel.accept(new TelegramMessageId(2), new MessageContent("Release 12.0: 50% faster", List.of(photo)),
                null, Instant.parse("2026-10-09T06:00:00Z"), clock).orElseThrow();
        var unrelated = other.accept(new TelegramMessageId(1), MessageContent.text("Выбор редакции"),
                null, Instant.parse("2026-10-09T07:00:00Z"), clock).orElseThrow();
        List.of(elections, release, unrelated).forEach(messages::save);
        messages.save(elections); // duplicate save is a no-op

        assertThat(messages.exists(release.id())).isTrue();
        assertThat(messages.findLatestBySource(channel.id(), 10)).extracting(m -> m.id().telegramMessageId().value())
                .containsExactly(2L, 1L);
        assertThat(messages.findLatestBySource(channel.id(), 10).getFirst().content().attachments()).containsExactly(photo);

        assertThat(search("ВЫБОР", Set.of(), null)).extracting(m -> m.content().text())
                .containsExactly("Выбор редакции", "Выборы прошли спокойно");
        assertThat(search("выбор", Set.of(channel.id()), null)).hasSize(1);
        assertThat(search("50%", Set.of(), null)).extracting(m -> m.id()).containsExactly(release.id());
        assertThat(search("0_", Set.of(), null)).isEmpty(); // LIKE wildcards are escaped
        assertThat(search(null, Set.of(), Instant.parse("2026-10-09T00:00:00Z"))).hasSize(2);
    }

    private List<io.github.dmytroha.tgconnector.domain.message.Message> search(
            String text, Set<io.github.dmytroha.tgconnector.domain.source.SourceId> ids, Instant from) {
        return messages.search(new MessageCriteria(Optional.ofNullable(text), ids, Optional.ofNullable(from), 50));
    }
}
