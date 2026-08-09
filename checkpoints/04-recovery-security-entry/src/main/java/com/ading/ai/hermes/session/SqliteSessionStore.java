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
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class SqliteSessionStore {

    private static final int MAX_BUSY_RETRIES = 8;
    private static final long PASSIVE_CHECKPOINT_INTERVAL = 100;

    private final Path database;
    private final ObjectMapper objectMapper;
    private final AtomicLong committedWrites = new AtomicLong();

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

    public void append(SessionId sessionId, AgentEvent event) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(event, "event must not be null");
        int attempt = 0;
        while (true) {
            try {
                appendInTransaction(sessionId, event);
                checkpointPeriodically();
                return;
            } catch (SQLException error) {
                if (!isBusy(error) || attempt >= MAX_BUSY_RETRIES) {
                    throw new SessionStoreException("failed to append SQLite session event", error);
                }
                pauseBeforeRetry(++attempt);
            } catch (IOException error) {
                throw new SessionStoreException("failed to append SQLite session event", error);
            }
        }
    }

    public void create(SessionId sessionId, String source, SessionId parentSessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        source = requireSource(source);
        try (Connection connection = open()) {
            beginImmediate(connection);
            try {
                SessionId root = parentSessionId == null
                        ? sessionId
                        : requireLineage(connection, parentSessionId).rootSessionId();
                insertSession(connection, sessionId, source, parentSessionId, root);
                commit(connection);
            } catch (SQLException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException error) {
            throw new SessionStoreException("failed to create SQLite session", error);
        }
    }

    public void fork(SessionId parentSessionId, SessionId childSessionId, String source) {
        Objects.requireNonNull(parentSessionId, "parentSessionId must not be null");
        Objects.requireNonNull(childSessionId, "childSessionId must not be null");
        source = requireSource(source);
        try (Connection connection = open()) {
            beginImmediate(connection);
            try {
                SessionLineage parent = requireLineage(connection, parentSessionId);
                insertSession(connection, childSessionId, source, parentSessionId, parent.rootSessionId());
                copyConfiguration(connection, parentSessionId, childSessionId);
                copyMessages(connection, parentSessionId, childSessionId);
                commit(connection);
            } catch (SQLException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException error) {
            throw new SessionStoreException("failed to fork SQLite session", error);
        }
    }

    public Optional<SessionLineage> lineage(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try (Connection connection = open()) {
            return findLineage(connection, sessionId);
        } catch (SQLException error) {
            throw new SessionStoreException("failed to load SQLite session lineage", error);
        }
    }

    public void configure(SessionId sessionId, Path workingDirectory, String model) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        SessionConfiguration configuration = new SessionConfiguration(workingDirectory, model);
        int attempt = 0;
        while (true) {
            try (Connection connection = open()) {
                beginImmediate(connection);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE sessions
                        SET working_directory = ?, model = ?
                        WHERE id = ?
                        """)) {
                    statement.setString(1, configuration.workingDirectory().toString());
                    statement.setString(2, configuration.model());
                    statement.setString(3, sessionId.value());
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("session does not exist: " + sessionId.value());
                    }
                    commit(connection);
                    return;
                } catch (SQLException error) {
                    rollback(connection, error);
                    throw error;
                }
            } catch (SQLException error) {
                if (!isBusy(error) || attempt >= MAX_BUSY_RETRIES) {
                    throw new SessionStoreException("failed to configure SQLite session", error);
                }
                pauseBeforeRetry(++attempt);
            }
        }
    }

    public Optional<SessionConfiguration> configuration(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT working_directory, model
                     FROM sessions
                     WHERE id = ?
                     """)) {
            statement.setString(1, sessionId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                String workingDirectory = row.getString(1);
                String model = row.getString(2);
                if (workingDirectory == null || model == null) {
                    return Optional.empty();
                }
                return Optional.of(new SessionConfiguration(
                        Path.of(workingDirectory),
                        model
                ));
            }
        } catch (SQLException error) {
            throw new SessionStoreException("failed to load SQLite session configuration", error);
        }
    }

    public List<SessionLineage> listLineages(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, source, parent_session_id, root_session_id
                     FROM sessions
                     ORDER BY created_at DESC, id
                     LIMIT ?
                     """)) {
            statement.setInt(1, limit);
            List<SessionLineage> sessions = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    SessionId id = new SessionId(rows.getString(1));
                    String parent = rows.getString(3);
                    String root = rows.getString(4);
                    sessions.add(new SessionLineage(
                            id,
                            rows.getString(2),
                            parent == null ? Optional.empty() : Optional.of(new SessionId(parent)),
                            new SessionId(root == null ? id.value() : root)
                    ));
                }
            }
            return List.copyOf(sessions);
        } catch (SQLException error) {
            throw new SessionStoreException("failed to list SQLite sessions", error);
        }
    }

    private void appendInTransaction(SessionId sessionId, AgentEvent event)
            throws SQLException, IOException {
        try (Connection connection = open()) {
            beginImmediate(connection);
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
                commit(connection);
            } catch (SQLException | IOException error) {
                rollback(connection, error);
                throw error;
            }
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
                "INSERT OR IGNORE INTO sessions(id, root_session_id) VALUES (?, ?)"
        )) {
            statement.setString(1, sessionId.value());
            statement.setString(2, sessionId.value());
            statement.executeUpdate();
        }
    }

    private static void insertSession(
            Connection connection,
            SessionId sessionId,
            String source,
            SessionId parentSessionId,
            SessionId rootSessionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sessions(id, source, parent_session_id, root_session_id)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, sessionId.value());
            statement.setString(2, source);
            if (parentSessionId == null) {
                statement.setNull(3, java.sql.Types.VARCHAR);
            } else {
                statement.setString(3, parentSessionId.value());
            }
            statement.setString(4, rootSessionId.value());
            statement.executeUpdate();
        }
    }

    private static Optional<SessionLineage> findLineage(
            Connection connection,
            SessionId sessionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT source, parent_session_id, root_session_id
                FROM sessions
                WHERE id = ?
                """)) {
            statement.setString(1, sessionId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                String parent = row.getString(2);
                String root = row.getString(3);
                return Optional.of(new SessionLineage(
                        sessionId,
                        row.getString(1),
                        parent == null ? Optional.empty() : Optional.of(new SessionId(parent)),
                        new SessionId(root == null ? sessionId.value() : root)
                ));
            }
        }
    }

    private static SessionLineage requireLineage(
            Connection connection,
            SessionId sessionId
    ) throws SQLException {
        return findLineage(connection, sessionId).orElseThrow(() ->
                new SQLException("parent session does not exist: " + sessionId.value()));
    }

    private static void copyMessages(
            Connection connection,
            SessionId parentSessionId,
            SessionId childSessionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO messages(session_id, event_index, kind, event_json, searchable_text)
                SELECT ?, event_index, kind, event_json, searchable_text
                FROM messages
                WHERE session_id = ?
                ORDER BY event_index
                """)) {
            statement.setString(1, childSessionId.value());
            statement.setString(2, parentSessionId.value());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO messages_fts(rowid, searchable_text, session_id, message_id)
                SELECT id, searchable_text, session_id, id
                FROM messages
                WHERE session_id = ?
                """)) {
            statement.setString(1, childSessionId.value());
            statement.executeUpdate();
        }
    }

    private static void copyConfiguration(
            Connection connection,
            SessionId parentSessionId,
            SessionId childSessionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE sessions
                SET working_directory = (
                        SELECT working_directory FROM sessions WHERE id = ?
                    ),
                    model = (
                        SELECT model FROM sessions WHERE id = ?
                    )
                WHERE id = ?
                """)) {
            statement.setString(1, parentSessionId.value());
            statement.setString(2, parentSessionId.value());
            statement.setString(3, childSessionId.value());
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
            case USER_MESSAGE, CONTEXT_SUMMARY, ERROR_RECOVERED,
                    RUN_INTERRUPTED, MODEL_FINAL_ANSWER -> event.text();
            case TOOL_REQUESTED -> event.toolRequest().name() + " " + event.toolRequest().arguments();
            case TOOL_OBSERVED -> event.toolObservation().callId() + " " + event.toolObservation().content();
        };
    }

    private void checkpointPeriodically() {
        if (committedWrites.incrementAndGet() % PASSIVE_CHECKPOINT_INTERVAL != 0) {
            return;
        }
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(PASSIVE)");
        } catch (SQLException ignored) {
            // The committed event is authoritative; a busy passive checkpoint can wait.
        }
    }

    private static void beginImmediate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
        }
    }

    private static void commit(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("COMMIT");
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private static boolean isBusy(SQLException error) {
        for (SQLException current = error; current != null; current = current.getNextException()) {
            String message = current.getMessage();
            if (current.getErrorCode() == 5
                    || current.getErrorCode() == 6
                    || message != null && (message.contains("SQLITE_BUSY")
                    || message.contains("database is locked"))) {
                return true;
            }
        }
        return false;
    }

    private static void pauseBeforeRetry(int attempt) {
        long upperBound = Math.min(250L, 20L * attempt + 20L);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10L, upperBound));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SessionStoreException("interrupted while waiting for SQLite", error);
        }
    }

    private static String requireSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        return source.trim();
    }
}
