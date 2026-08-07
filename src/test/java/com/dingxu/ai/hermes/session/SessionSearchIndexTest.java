package com.dingxu.ai.hermes.session;

import com.dingxu.ai.hermes.core.AgentEvent;
import com.dingxu.ai.hermes.core.AgentEventKind;
import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionSearchIndexTest {

    @Test
    void findsMatchingUserMessagesAcrossSessions() {
        SessionSearchIndex index = new SessionSearchIndex();
        index.add(new SessionRecord(new SessionId("s1"), List.of(
                AgentEvent.userMessage("fix payment timeout"),
                AgentEvent.modelFinalAnswer("done")
        )));
        index.add(new SessionRecord(new SessionId("s2"), List.of(
                AgentEvent.userMessage("review memory policy"),
                AgentEvent.modelFinalAnswer("memory boundary added")
        )));

        List<SessionSearchHit> hits = index.search("payment", 10);

        assertEquals(1, hits.size());
        assertEquals(new SessionId("s1"), hits.get(0).sessionId());
        assertEquals(0, hits.get(0).eventIndex());
        assertEquals(AgentEventKind.USER_MESSAGE, hits.get(0).kind());
        assertEquals("fix payment timeout", hits.get(0).snippet());
    }

    @Test
    void findsToolRequestsAndObservations() {
        SessionSearchIndex index = new SessionSearchIndex();
        index.add(new SessionRecord(new SessionId("s1"), List.of(
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "Hermes uses sessions for recall."))
        )));

        List<SessionSearchHit> hits = index.search("README", 10);
        List<SessionSearchHit> observationHits = index.search("recall", 10);

        assertEquals("tool read_file {path=README.md}", hits.get(0).snippet());
        assertEquals("tool result call-1 success=true Hermes uses sessions for recall.", observationHits.get(0).snippet());
    }

    @Test
    void searchIsCaseInsensitiveAndKeepsInsertionOrder() {
        SessionSearchIndex index = new SessionSearchIndex();
        index.add(new SessionRecord(new SessionId("old"), List.of(
                AgentEvent.userMessage("Memory belongs to long term facts")
        )));
        index.add(new SessionRecord(new SessionId("new"), List.of(
                AgentEvent.modelFinalAnswer("memory is not a task diary")
        )));

        List<SessionSearchHit> hits = index.search("MEMORY", 10);

        assertEquals(List.of(new SessionId("old"), new SessionId("new")),
                hits.stream().map(SessionSearchHit::sessionId).toList());
    }

    @Test
    void limitsResults() {
        SessionSearchIndex index = new SessionSearchIndex();
        index.add(new SessionRecord(new SessionId("s1"), List.of(
                AgentEvent.userMessage("memory one"),
                AgentEvent.modelFinalAnswer("memory two")
        )));

        List<SessionSearchHit> hits = index.search("memory", 1);

        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).eventIndex());
    }

    @Test
    void findsInterruptedRunReasons() {
        SessionSearchIndex index = new SessionSearchIndex();
        index.add(new SessionRecord(new SessionId("s1"), List.of(
                AgentEvent.userMessage("scan repository"),
                AgentEvent.runInterrupted("user stopped full repository scan")
        )));

        List<SessionSearchHit> hits = index.search("stopped full", 10);

        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).eventIndex());
        assertEquals(AgentEventKind.RUN_INTERRUPTED, hits.get(0).kind());
        assertEquals("user stopped full repository scan", hits.get(0).snippet());
    }

    @Test
    void rejectsBlankQueryAndInvalidLimit() {
        SessionSearchIndex index = new SessionSearchIndex();

        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> index.search(" ", 10));
        IllegalArgumentException invalidLimit = assertThrows(IllegalArgumentException.class, () -> index.search("memory", 0));

        assertEquals("query must not be blank", blank.getMessage());
        assertEquals("limit must be positive", invalidLimit.getMessage());
    }
}
