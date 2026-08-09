package com.ading.ai.hermes.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

final class SqliteSessionSchema {

    private static final int CURRENT_VERSION = 3;

    private SqliteSessionSchema() {
    }

    static void initialize(Path database) {
        try {
            Path parent = database.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException error) {
            throw new SessionStoreException("failed to create SQLite directory", error);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO schema_version(version)
                    SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM schema_version)
                    """);
            int version;
            try (ResultSet result = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                if (!result.next()) {
                    throw new SessionStoreException("SQLite schema version is missing", null);
                }
                version = result.getInt(1);
            }
            if (version > CURRENT_VERSION) {
                throw new SessionStoreException(
                        "SQLite schema version " + version
                                + " is newer than supported version " + CURRENT_VERSION,
                        null
                );
            }
            if (version < 0) {
                throw new SessionStoreException("SQLite schema version must not be negative", null);
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT PRIMARY KEY,
                        source TEXT NOT NULL DEFAULT 'unknown',
                        parent_session_id TEXT REFERENCES sessions(id),
                        root_session_id TEXT,
                        working_directory TEXT,
                        model TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            addColumnIfMissing(connection, statement, "source", "TEXT NOT NULL DEFAULT 'unknown'");
            addColumnIfMissing(connection, statement, "parent_session_id", "TEXT REFERENCES sessions(id)");
            addColumnIfMissing(connection, statement, "root_session_id", "TEXT");
            addColumnIfMissing(connection, statement, "working_directory", "TEXT");
            addColumnIfMissing(connection, statement, "model", "TEXT");
            statement.execute("UPDATE sessions SET root_session_id = id WHERE root_session_id IS NULL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id TEXT NOT NULL REFERENCES sessions(id),
                        event_index INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        event_json TEXT NOT NULL,
                        searchable_text TEXT NOT NULL,
                        UNIQUE(session_id, event_index)
                    )
                    """);
            statement.execute("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                        searchable_text,
                        session_id UNINDEXED,
                        message_id UNINDEXED,
                        tokenize='trigram'
                    )
                    """);
            if (version < CURRENT_VERSION) {
                statement.execute("UPDATE schema_version SET version = " + CURRENT_VERSION);
            }
        } catch (SQLException error) {
            throw new SessionStoreException("failed to initialize SQLite session store", error);
        }
    }

    private static void addColumnIfMissing(
            Connection connection,
            Statement statement,
            String column,
            String declaration
    ) throws SQLException {
        boolean present = false;
        try (ResultSet columns = connection.getMetaData().getColumns(
                null,
                null,
                "sessions",
                column
        )) {
            present = columns.next();
        }
        if (!present) {
            statement.execute("ALTER TABLE sessions ADD COLUMN " + column + " " + declaration);
        }
    }
}
