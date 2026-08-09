package com.ading.ai.hermes.acp;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.session.SessionId;
import com.ading.ai.hermes.session.SqliteSessionStore;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermesAcpAgentTest {

    @TempDir
    Path workspace;

    @Test
    void mapsTypedAcpSessionsPromptsModelConfigForkAndHistoryToTheRuntime() {
        SqliteSessionStore sessions = new SqliteSessionStore(workspace.resolve("sessions.db"));
        AtomicReference<com.ading.ai.hermes.core.AgentRunRequest> captured = new AtomicReference<>();
        HermesAcpAgent agent = new HermesAcpAgent(
                request -> {
                    captured.set(request);
                    List<AgentEvent> events = List.of(
                            AgentEvent.userMessage(request.userMessage()),
                            AgentEvent.toolRequested(new ToolRequest(
                                    "call-1",
                                    "read_file",
                                    Map.of("path", "README.md")
                            )),
                            AgentEvent.toolObserved(ToolObservation.success(
                                    "call-1",
                                    "workspace ready"
                            )),
                            AgentEvent.modelFinalAnswer("ACP 已执行")
                    );
                    events.forEach(event -> sessions.append(
                            new SessionId(request.conversationId()),
                            event
                    ));
                    return new AgentRunResult(
                            FinishReason.FINAL_ANSWER,
                            "ACP 已执行",
                            new AgentState(events, 1)
                    );
                },
                sessionId -> { },
                sessions,
                workspace,
                "default-model"
        );

        var initialized = agent.initialize(new AcpSchema.InitializeRequest(
                1,
                new AcpSchema.ClientCapabilities()
        ));
        var created = agent.newSession(new AcpSchema.NewSessionRequest(
                workspace.toString(),
                List.of()
        ));
        List<AcpSchema.SessionUpdate> updates = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        SyncPromptContext context = context(created.sessionId(), updates, messages);

        var prompted = agent.prompt(new AcpSchema.PromptRequest(
                created.sessionId(),
                List.of(new AcpSchema.TextContent("检查 README"))
        ), context);

        assertEquals(1, initialized.protocolVersion());
        assertTrue(initialized.agentCapabilities().loadSession());
        assertNotNull(initialized.agentCapabilities().sessionCapabilities().fork());
        assertEquals(AcpSchema.StopReason.END_TURN, prompted.stopReason());
        assertEquals("default-model", captured.get().metadata().get("model"));
        assertTrue(updates.stream().anyMatch(AcpSchema.ToolCall.class::isInstance));
        assertTrue(updates.stream().anyMatch(
                AcpSchema.ToolCallUpdateNotification.class::isInstance
        ));
        assertEquals(List.of("ACP 已执行"), messages);

        agent.setSessionConfigOption(AcpSchema.SetSessionConfigOptionRequest.select(
                created.sessionId(),
                "model",
                "session-model"
        ));
        agent.prompt(new AcpSchema.PromptRequest(
                created.sessionId(),
                List.of(new AcpSchema.TextContent("再次检查"))
        ), context);
        assertEquals("session-model", captured.get().metadata().get("model"));

        var forked = agent.forkSession(new AcpSchema.ForkSessionRequest(
                created.sessionId(),
                workspace.toString(),
                List.of()
        ));
        List<AcpSchema.SessionUpdate> replayed = new ArrayList<>();
        agent.loadSession(new AcpSchema.LoadSessionRequest(
                forked.sessionId(),
                workspace.toString(),
                List.of()
        ), replayed::add);
        assertTrue(replayed.stream().anyMatch(AcpSchema.UserMessageChunk.class::isInstance));
        assertTrue(replayed.stream().anyMatch(AcpSchema.AgentMessageChunk.class::isInstance));
        assertEquals(2, agent.listSessions(new AcpSchema.ListSessionsRequest(null))
                .sessions().size());
    }

    @Test
    void mapsAcpCancellationToTheSharedCancellationPort() {
        SqliteSessionStore sessions = new SqliteSessionStore(workspace.resolve("cancel.db"));
        AtomicReference<String> cancelledSession = new AtomicReference<>();
        HermesAcpAgent agent = new HermesAcpAgent(
                request -> { throw new AssertionError("not used"); },
                cancelledSession::set,
                sessions,
                workspace,
                "default-model"
        );
        var created = agent.newSession(new AcpSchema.NewSessionRequest(
                workspace.toString(),
                List.of()
        ));
        agent.cancel(new AcpSchema.CancelNotification(created.sessionId()));

        assertEquals(created.sessionId(), cancelledSession.get());
    }

    private static SyncPromptContext context(
            String sessionId,
            List<AcpSchema.SessionUpdate> updates,
            List<String> messages
    ) {
        return (SyncPromptContext) Proxy.newProxyInstance(
                SyncPromptContext.class.getClassLoader(),
                new Class<?>[]{SyncPromptContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSessionId" -> sessionId;
                    case "sendUpdate" -> {
                        updates.add((AcpSchema.SessionUpdate) args[1]);
                        yield null;
                    }
                    case "sendMessage" -> {
                        messages.add((String) args[0]);
                        yield null;
                    }
                    case "toString" -> "RecordingSyncPromptContext";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
