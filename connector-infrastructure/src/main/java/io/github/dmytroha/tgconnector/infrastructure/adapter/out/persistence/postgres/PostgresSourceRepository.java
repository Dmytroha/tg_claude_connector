package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence.postgres;

import io.github.dmytroha.tgconnector.domain.source.ChatReference;
import io.github.dmytroha.tgconnector.domain.source.ConcurrentSourceModificationException;
import io.github.dmytroha.tgconnector.domain.source.DuplicateSourceReferenceException;
import io.github.dmytroha.tgconnector.domain.source.IngestionMode;
import io.github.dmytroha.tgconnector.domain.source.Source;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.SourceRepository;
import io.github.dmytroha.tgconnector.domain.source.SourceStatus;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Driven adapter: stores {@link Source} aggregates in PostgreSQL, with optimistic locking on {@code version}.
 */
@Repository
@ConditionalOnProperty(prefix = "connector", name = "persistence", havingValue = "postgres", matchIfMissing = true)
public class PostgresSourceRepository implements SourceRepository {

    private static final String COLUMNS = "id, reference, title, status, last_read_message_id, registered_at, version";

    private static final RowMapper<Source> ROW_MAPPER = (rs, rowNum) -> {
        var lastRead = rs.getObject("last_read_message_id", Long.class);
        return Source.restore(
                new SourceId(rs.getObject("id", java.util.UUID.class)),
                ChatReference.parse(rs.getString("reference")),
                rs.getString("title"),
                SourceStatus.valueOf(rs.getString("status")),
                lastRead == null ? null : new TelegramMessageId(lastRead),
                rs.getTimestamp("registered_at").toInstant(),
                rs.getLong("version"));
    };

    private final JdbcClient jdbc;

    public PostgresSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Source save(Source source) {
        return source.version() == 0 ? insert(source) : update(source);
    }

    private Source insert(Source source) {
        try {
            jdbc.sql("""
                            INSERT INTO sources (%s)
                            VALUES (:id, :reference, :title, :status, :lastRead, :registeredAt, 1)""".formatted(COLUMNS))
                    .params(params(source))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateSourceReferenceException(source.reference());
        }
        source.markPersisted(1);
        return source;
    }

    private Source update(Source source) {
        var updated = jdbc.sql("""
                        UPDATE sources
                        SET title = :title, status = :status, last_read_message_id = :lastRead, version = version + 1
                        WHERE id = :id AND version = :version""")
                .params(params(source))
                .param("version", source.version())
                .update();
        if (updated == 0) {
            throw new ConcurrentSourceModificationException(source.id());
        }
        source.markPersisted(source.version() + 1);
        return source;
    }

    private static java.util.Map<String, Object> params(Source source) {
        var params = new java.util.HashMap<String, Object>();
        params.put("id", source.id().value());
        params.put("reference", source.reference().asString());
        params.put("title", source.title());
        params.put("status", source.status().name());
        params.put("lastRead", source.lastReadMessageId().map(TelegramMessageId::value).orElse(null));
        params.put("registeredAt", Timestamp.from(source.registeredAt()));
        return params;
    }

    @Override
    public Optional<Source> findById(SourceId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sources WHERE id = :id")
                .param("id", id.value())
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Source> findByReference(ChatReference reference) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sources WHERE reference = :reference")
                .param("reference", reference.asString())
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<Source> findActiveByIngestionMode(IngestionMode mode) {
        // ingestion mode is derived from the reference, so filter in memory; the number of sources is small
        return jdbc.sql("SELECT " + COLUMNS + " FROM sources WHERE status = 'ACTIVE' ORDER BY registered_at")
                .query(ROW_MAPPER)
                .list().stream()
                .filter(s -> s.ingestionMode() == mode)
                .toList();
    }

    @Override
    public List<Source> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sources ORDER BY registered_at").query(ROW_MAPPER).list();
    }
}
