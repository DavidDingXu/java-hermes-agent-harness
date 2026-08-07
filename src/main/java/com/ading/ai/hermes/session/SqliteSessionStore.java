package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteSessionStore {

    private final Path database;
    private final ObjectMapper objectMapper;

    public SqliteSessionStore(Path database) {
        this(database, new ObjectMapper());
    }

    SqliteSessionStore(Path database, ObjectMapper objectMapper) {
        this.database = Objects.requireNonNull(database, "database must not be null")
                .toAbsolutePath()
                .normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        SqliteSessionSchema.initialize(this.database);
    }

    public synchronized void append(SessionId sessionId, AgentEvent event) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(event, "event must not be null");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                ensureSession(connection, sessionId);
                int eventIndex = nextEventIndex(connection, sessionId);
                String eventJson = objectMapper.writeValueAsString(event);
                String searchable = searchableText(event);
                long messageId = insertMessage(
                        connection, sessionId, eventIndex, event, eventJson, searchable
                );
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO messages_fts(rowid, searchable_text, session_id, message_id)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, messageId);
                    statement.setString(2, searchable);
                    statement.setString(3, sessionId.value());
                    statement.setLong(4, messageId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException | IOException error) {
            throw new SessionStoreException("failed to append SQLite session event", error);
        }
    }

    public SessionRecord load(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT event_json
                     FROM messages
                     WHERE session_id = ?
                     ORDER BY event_index
                     """)) {
            statement.setString(1, sessionId.value());
            List<AgentEvent> events = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    events.add(objectMapper.readValue(rows.getString(1), AgentEvent.class));
                }
            }
            return new SessionRecord(sessionId, events);
        } catch (SQLException | IOException error) {
            throw new SessionStoreException("failed to load SQLite session", error);
        }
    }

    public List<SessionSearchHit> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String sql = query.codePointCount(0, query.length()) < 3
                ? """
                  SELECT session_id, event_index, kind, searchable_text
                  FROM messages
                  WHERE searchable_text LIKE ?
                  ORDER BY id
                  LIMIT ?
                  """
                : """
                  SELECT m.session_id, m.event_index, m.kind, m.searchable_text
                  FROM messages_fts f
                  JOIN messages m ON m.id = f.rowid
                  WHERE messages_fts MATCH ?
                  ORDER BY bm25(messages_fts), m.id
                  LIMIT ?
                  """;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, query.codePointCount(0, query.length()) < 3
                    ? "%" + query + "%"
                    : "\"" + query.replace("\"", "\"\"") + "\"");
            statement.setInt(2, limit);
            List<SessionSearchHit> hits = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    hits.add(new SessionSearchHit(
                            new SessionId(rows.getString(1)),
                            rows.getInt(2),
                            AgentEventKind.valueOf(rows.getString(3)),
                            rows.getString(4)
                    ));
                }
            }
            return List.copyOf(hits);
        } catch (SQLException error) {
            throw new SessionStoreException("failed to search SQLite sessions", error);
        }
    }

    public String journalMode() {
        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("PRAGMA journal_mode")) {
            return row.next() ? row.getString(1).toLowerCase() : "";
        } catch (SQLException error) {
            throw new SessionStoreException("failed to read SQLite journal mode", error);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=1000");
        }
        return connection;
    }

    private static void ensureSession(Connection connection, SessionId sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO sessions(id) VALUES (?)"
        )) {
            statement.setString(1, sessionId.value());
            statement.executeUpdate();
        }
    }

    private static int nextEventIndex(Connection connection, SessionId sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(event_index), -1) + 1 FROM messages WHERE session_id = ?"
        )) {
            statement.setString(1, sessionId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        }
    }

    private static long insertMessage(
            Connection connection,
            SessionId sessionId,
            int eventIndex,
            AgentEvent event,
            String eventJson,
            String searchable
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO messages(session_id, event_index, kind, event_json, searchable_text)
                VALUES (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sessionId.value());
            statement.setInt(2, eventIndex);
            statement.setString(3, event.kind().name());
            statement.setString(4, eventJson);
            statement.setString(5, searchable);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return a message id");
                }
                return keys.getLong(1);
            }
        }
    }

    private static String searchableText(AgentEvent event) {
        return switch (event.kind()) {
            case USER_MESSAGE, CONTEXT_SUMMARY, ERROR_RECOVERED, RUN_INTERRUPTED, MODEL_FINAL_ANSWER -> event.text();
            case TOOL_REQUESTED -> event.toolRequest().name() + " " + event.toolRequest().arguments();
            case TOOL_OBSERVED -> event.toolObservation().callId() + " " + event.toolObservation().content();
        };
    }
}
