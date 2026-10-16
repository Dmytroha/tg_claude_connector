package db.migration;

import io.github.dmytroha.tgconnector.domain.message.Hashtags;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills {@code messages.hashtags} for messages stored before V3, with the same extraction rules the
 * application uses for new messages (done in Java, so it does not depend on the database's locale).
 */
public class V4__backfill_message_hashtags extends BaseJavaMigration {

    private record Row(Object sourceId, long messageId, String text) {
    }

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        var rows = new ArrayList<Row>();
        try (var select = connection.createStatement();
             var result = select.executeQuery("SELECT source_id, message_id, text FROM messages")) {
            while (result.next()) {
                rows.add(new Row(result.getObject(1), result.getLong(2), result.getString(3)));
            }
        }
        try (var update = connection.prepareStatement(
                "UPDATE messages SET hashtags = ? WHERE source_id = ? AND message_id = ?")) {
            for (var row : rows) {
                var tags = List.copyOf(Hashtags.extract(row.text()));
                update.setArray(1, connection.createArrayOf("text", tags.toArray()));
                update.setObject(2, row.sourceId());
                update.setLong(3, row.messageId());
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
