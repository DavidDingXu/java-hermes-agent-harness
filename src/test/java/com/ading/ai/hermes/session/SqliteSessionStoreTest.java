package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteSessionStoreTest {

    @TempDir
    Path directory;

    @Test
    void persistsOrderedEventsAcrossStoreInstancesWithWalEnabled() {
        Path database = directory.resolve("state.db");
        SessionId sessionId = new SessionId("session-1");
        SqliteSessionStore first = new SqliteSessionStore(database);
        first.append(sessionId, AgentEvent.userMessage("检查工作区配置"));
        first.append(sessionId, AgentEvent.toolRequested(
                new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))
        ));
        first.append(sessionId, AgentEvent.toolObserved(
                ToolObservation.success("call-1", "workspace ready")
        ));

        SqliteSessionStore reopened = new SqliteSessionStore(database);
        SessionRecord record = reopened.load(sessionId);

        assertEquals(3, record.events().size());
        assertEquals("检查工作区配置", record.events().getFirst().text());
        assertEquals("wal", reopened.journalMode());
    }

    @Test
    void searchesChineseAndToolEvidenceThroughFtsIndex() {
        SqliteSessionStore store = new SqliteSessionStore(directory.resolve("state.db"));
        SessionId first = new SessionId("first");
        SessionId second = new SessionId("second");
        store.append(first, AgentEvent.userMessage("修复工作区路径校验"));
        store.append(second, AgentEvent.userMessage("检查网关认证"));
        store.append(second, AgentEvent.toolObserved(
                ToolObservation.failure("call-9", "credential expired")
        ));

        List<SessionSearchHit> chinese = store.search("工作区", 10);
        List<SessionSearchHit> toolEvidence = store.search("credential", 10);

        assertEquals(List.of(first), chinese.stream().map(SessionSearchHit::sessionId).toList());
        assertEquals(List.of(second), toolEvidence.stream().map(SessionSearchHit::sessionId).toList());
    }

    @Test
    void refusesDatabaseCreatedByANewerSchemaVersion() throws Exception {
        Path database = directory.resolve("future.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO schema_version(version) VALUES (2)");
        }

        SessionStoreException error = assertThrows(
                SessionStoreException.class,
                () -> new SqliteSessionStore(database)
        );

        assertEquals("SQLite schema version 2 is newer than supported version 1", error.getMessage());
    }
}
