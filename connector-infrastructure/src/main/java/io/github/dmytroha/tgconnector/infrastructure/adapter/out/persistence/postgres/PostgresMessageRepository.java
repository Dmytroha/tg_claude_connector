package io.github.dmytroha.tgconnector.infrastructure.adapter.out.persistence.postgres;

import io.github.dmytroha.tgconnector.domain.message.Attachment;
import io.github.dmytroha.tgconnector.domain.message.Author;
import io.github.dmytroha.tgconnector.domain.message.Message;
import io.github.dmytroha.tgconnector.domain.message.MessageContent;
import io.github.dmytroha.tgconnector.domain.message.MessageCriteria;
import io.github.dmytroha.tgconnector.domain.message.MessageKey;
import io.github.dmytroha.tgconnector.domain.message.MessageRepository;
import io.github.dmytroha.tgconnector.domain.source.SourceId;
import io.github.dmytroha.tgconnector.domain.source.TelegramMessageId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Driven adapter: stores {@link Message} aggregates in PostgreSQL. Attachments are kept as JSONB.
 */
@Repository
@ConditionalOnProperty(prefix = "connector", name = "persistence", havingValue = "postgres", matchIfMissing = true)
public class PostgresMessageRepository implements MessageRepository {

    private static final String COLUMNS =
            "source_id, message_id, text, attachments, author_name, author_username, posted_at, received_at";
    private static final String INSERT_COLUMNS = COLUMNS + ", hashtags";

    private record AttachmentJson(String kind, String reference) {
    }

    private static final TypeReference<List<AttachmentJson>> ATTACHMENTS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    private final RowMapper<Message> rowMapper = (rs, rowNum) -> Message.restore(
            new MessageKey(new SourceId(rs.getObject("source_id", UUID.class)),
                    new TelegramMessageId(rs.getLong("message_id"))),
            new MessageContent(rs.getString("text"), readAttachments(rs.getString("attachments"))),
            new Author(rs.getString("author_name"), rs.getString("author_username")),
            rs.getTimestamp("posted_at").toInstant(),
            rs.getTimestamp("received_at").toInstant());

    public PostgresMessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Message save(Message message) {
        jdbc.sql("""
                        INSERT INTO messages (%s)
                        VALUES (:sourceId, :messageId, :text, CAST(:attachments AS JSONB), :authorName, :authorUsername,
                                :postedAt, :receivedAt, string_to_array(:hashtags, ','))
                        ON CONFLICT (source_id, message_id) DO NOTHING""".formatted(INSERT_COLUMNS))
                .param("sourceId", message.id().sourceId().value())
                .param("messageId", message.id().telegramMessageId().value())
                .param("text", message.content().text())
                .param("attachments", writeAttachments(message.content().attachments()))
                .param("authorName", message.author().displayName())
                .param("authorUsername", message.author().username())
                .param("postedAt", Timestamp.from(message.postedAt()))
                .param("receivedAt", Timestamp.from(message.receivedAt()))
                // hashtags contain only letters, digits and '_', so joining with ',' is unambiguous
                .param("hashtags", String.join(",", message.content().hashtags()))
                .update();
        return message;
    }

    @Override
    public boolean exists(MessageKey key) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM messages WHERE source_id = :sourceId AND message_id = :messageId)")
                .param("sourceId", key.sourceId().value())
                .param("messageId", key.telegramMessageId().value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<Message> findLatestBySource(SourceId sourceId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM messages WHERE source_id = :sourceId ORDER BY message_id DESC LIMIT :limit")
                .param("sourceId", sourceId.value())
                .param("limit", limit)
                .query(rowMapper)
                .list();
    }

    @Override
    public List<Message> search(MessageCriteria criteria) {
        var where = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        criteria.text().ifPresent(text -> {
            where.add("text ILIKE :pattern ESCAPE '\\'");
            params.put("pattern", "%" + escapeLike(text) + "%");
        });
        if (!criteria.sourceIds().isEmpty()) {
            where.add("source_id IN (:sourceIds)");
            params.put("sourceIds", criteria.sourceIds().stream().map(SourceId::value).toList());
        }
        criteria.postedFrom().ifPresent(from -> {
            where.add("posted_at >= :postedFrom");
            params.put("postedFrom", Timestamp.from(from));
        });
        if (!criteria.hashtags().isEmpty()) {
            where.add("hashtags && ARRAY[:hashtags]::text[]");
            params.put("hashtags", List.copyOf(criteria.hashtags()));
        }
        if (!criteria.excludedHashtags().isEmpty()) {
            where.add("NOT (hashtags && ARRAY[:excludedHashtags]::text[])");
            params.put("excludedHashtags", List.copyOf(criteria.excludedHashtags()));
        }
        params.put("limit", criteria.limit());
        var sql = "SELECT " + COLUMNS + " FROM messages"
                + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY posted_at DESC, message_id DESC LIMIT :limit";
        return jdbc.sql(sql).params(params).query(rowMapper).list();
    }

    @Override
    public List<HashtagCount> countHashtags(Set<SourceId> sourceIds, Optional<Instant> postedFrom) {
        var where = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        if (!sourceIds.isEmpty()) {
            where.add("source_id IN (:sourceIds)");
            params.put("sourceIds", sourceIds.stream().map(SourceId::value).toList());
        }
        postedFrom.ifPresent(from -> {
            where.add("posted_at >= :postedFrom");
            params.put("postedFrom", Timestamp.from(from));
        });
        var sql = "SELECT tag, count(*) AS messages FROM messages, unnest(hashtags) AS tag"
                + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " GROUP BY tag ORDER BY messages DESC, tag";
        return jdbc.sql(sql).params(params)
                .query((rs, rowNum) -> new HashtagCount(rs.getString("tag"), rs.getLong("messages")))
                .list();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String writeAttachments(List<Attachment> attachments) {
        return json.writeValueAsString(attachments.stream()
                .map(a -> new AttachmentJson(a.kind().name(), a.reference()))
                .toList());
    }

    private List<Attachment> readAttachments(String value) {
        return json.readValue(value, ATTACHMENTS).stream()
                .map(a -> new Attachment(Attachment.Kind.valueOf(a.kind()), a.reference()))
                .toList();
    }
}
