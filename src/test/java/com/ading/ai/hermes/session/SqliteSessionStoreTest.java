package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
            statement.execute("INSERT INTO schema_version(version) VALUES (4)");
        }

        SessionStoreException error = assertThrows(
                SessionStoreException.class,
                () -> new SqliteSessionStore(database)
        );

        assertEquals("SQLite schema version 4 is newer than supported version 3", error.getMessage());
    }

    @Test
    void recordsParentAndRootWhenAFreshSessionForks() {
        SqliteSessionStore store = new SqliteSessionStore(directory.resolve("lineage.db"));
        SessionId root = new SessionId("root-session");
        SessionId child = new SessionId("child-session");
        store.create(root, "cli", null);
        store.append(root, AgentEvent.userMessage("root context"));

        store.fork(root, child, "acp");

        SessionLineage lineage = store.lineage(child).orElseThrow();
        assertEquals(root, lineage.parentSessionId().orElseThrow());
        assertEquals(root, lineage.rootSessionId());
        assertEquals("acp", lineage.source());
        assertEquals(List.of(AgentEvent.userMessage("root context")), store.load(child).events());
    }

    @Test
    void persistsWorkingDirectoryAndModelAndCarriesThemIntoAFork() {
        Path database = directory.resolve("session-config.db");
        Path workspace = directory.resolve("workspace");
        SessionId root = new SessionId("configured-root");
        SessionId child = new SessionId("configured-child");
        SqliteSessionStore store = new SqliteSessionStore(database);
        store.create(root, "acp", null);
        store.configure(root, workspace, "gpt-reader");

        store.fork(root, child, "acp");

        SqliteSessionStore reopened = new SqliteSessionStore(database);
        SessionConfiguration rootConfig = reopened.configuration(root).orElseThrow();
        SessionConfiguration childConfig = reopened.configuration(child).orElseThrow();
        assertEquals(workspace.toAbsolutePath().normalize(), rootConfig.workingDirectory());
        assertEquals("gpt-reader", rootConfig.model());
        assertEquals(rootConfig.workingDirectory(), childConfig.workingDirectory());
        assertEquals(rootConfig.model(), childConfig.model());
    }

    @Test
    void serializesConcurrentWritersAcrossStoreInstances() throws Exception {
        Path database = directory.resolve("concurrent.db");
        SessionId sessionId = new SessionId("shared-session");
        SqliteSessionStore first = new SqliteSessionStore(database);
        SqliteSessionStore second = new SqliteSessionStore(database);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> appendRange(first, sessionId, "left", 30));
            var right = executor.submit(() -> appendRange(second, sessionId, "right", 30));
            left.get(10, TimeUnit.SECONDS);
            right.get(10, TimeUnit.SECONDS);
        }

        SessionRecord record = first.load(sessionId);
        assertEquals(60, record.events().size());
        assertEquals(60, record.events().stream().map(AgentEvent::text).distinct().count());
    }

    private static void appendRange(
            SqliteSessionStore store,
            SessionId sessionId,
            String prefix,
            int count
    ) {
        for (int index = 0; index < count; index++) {
            store.append(sessionId, AgentEvent.userMessage(prefix + "-" + index));
        }
    }
}
