package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSessionStoreTest {

    @TempDir
    Path sessionsDir;

    @Test
    void appendsAndLoadsSessionEventsInOrder() {
        FileSessionStore store = new FileSessionStore(sessionsDir);
        SessionId sessionId = new SessionId("session-1");

        store.append(sessionId, AgentEvent.userMessage("read README"));
        store.append(sessionId, AgentEvent.toolRequested(
                new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))
        ));
        store.append(sessionId, AgentEvent.toolObserved(ToolObservation.success("call-1", "content")));

        SessionRecord record = store.load(sessionId);

        assertEquals(sessionId, record.sessionId());
        assertEquals(List.of(
                AgentEvent.userMessage("read README"),
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "content"))
        ), record.events());
    }

    @Test
    void returnsEmptyRecordForMissingSession() {
        FileSessionStore store = new FileSessionStore(sessionsDir);

        SessionRecord record = store.load(new SessionId("missing"));

        assertEquals(List.of(), record.events());
    }

    @Test
    void isolatesSessionsIntoSeparateFiles() {
        FileSessionStore store = new FileSessionStore(sessionsDir);

        store.append(new SessionId("a"), AgentEvent.userMessage("A"));
        store.append(new SessionId("b"), AgentEvent.userMessage("B"));

        assertEquals(List.of(AgentEvent.userMessage("A")), store.load(new SessionId("a")).events());
        assertEquals(List.of(AgentEvent.userMessage("B")), store.load(new SessionId("b")).events());
    }

    @Test
    void rejectsUnsafeSessionId() {
        FileSessionStore store = new FileSessionStore(sessionsDir);

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> store.load(new SessionId("../secret"))
        );

        assertEquals("sessionId must contain only letters, numbers, dot, dash or underscore", error.getMessage());
    }

    @Test
    void createsSessionDirectoryWhenAppending() {
        Path nested = sessionsDir.resolve("nested");
        FileSessionStore store = new FileSessionStore(nested);

        store.append(new SessionId("session-1"), AgentEvent.userMessage("hello"));

        assertTrue(Files.exists(nested.resolve("session-1.jsonl")));
    }
}
