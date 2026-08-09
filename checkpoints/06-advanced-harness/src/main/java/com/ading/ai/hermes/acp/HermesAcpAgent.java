package com.ading.ai.hermes.acp;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.session.SessionConfiguration;
import com.ading.ai.hermes.session.SessionId;
import com.ading.ai.hermes.session.SessionLineage;
import com.ading.ai.hermes.session.SqliteSessionStore;
import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class HermesAcpAgent {

    private static final int PAGE_SIZE = 100;
    private static final String MODEL_CONFIG_ID = "model";

    private final AgentRuntime runtime;
    private final AcpCancellation cancellation;
    private final SqliteSessionStore sessions;
    private final Path workspace;
    private final String defaultModel;

    public HermesAcpAgent(
            AgentRuntime runtime,
            AcpCancellation cancellation,
            SqliteSessionStore sessions,
            Path workspace,
            String defaultModel
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.cancellation = Objects.requireNonNull(
                cancellation,
                "cancellation must not be null"
        );
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.workspace = realDirectory(Objects.requireNonNull(
                workspace,
                "workspace must not be null"
        ));
        if (defaultModel == null || defaultModel.isBlank()) {
            throw new IllegalArgumentException("defaultModel must not be blank");
        }
        this.defaultModel = defaultModel.trim();
    }

    public AcpSyncAgent create(AcpAgentTransport transport) {
        Objects.requireNonNull(transport, "transport must not be null");
        AtomicReference<AcpSyncAgent> agentReference = new AtomicReference<>();
        AcpSyncAgent agent = AcpAgent.sync(transport)
                .initializeHandler(this::initialize)
                .newSessionHandler(this::newSession)
                .loadSessionHandler(request -> loadSession(
                        request,
                        update -> agentReference.get().sendSessionUpdate(request.sessionId(), update)
                ))
                .resumeSessionHandler(request -> resumeSession(
                        request,
                        update -> agentReference.get().sendSessionUpdate(request.sessionId(), update)
                ))
                .forkSessionHandler(this::forkSession)
                .listSessionsHandler(this::listSessions)
                .setSessionConfigOptionHandler(this::setSessionConfigOption)
                .promptHandler(this::prompt)
                .cancelHandler(this::cancel)
                .build();
        agentReference.set(agent);
        return agent;
    }

    public void runStdio() {
        create(new StdioAcpAgentTransport()).run();
    }

    public AcpSchema.InitializeResponse initialize(AcpSchema.InitializeRequest request) {
        AcpSchema.SessionCapabilities sessionCapabilities = new AcpSchema.SessionCapabilities(
                Map.of(),
                null,
                Map.of(),
                null,
                null,
                Map.of()
        );
        AcpSchema.AgentCapabilities capabilities = new AcpSchema.AgentCapabilities(
                true,
                sessionCapabilities,
                new AcpSchema.McpCapabilities(false, false),
                new AcpSchema.PromptCapabilities(false, false, false),
                null
        );
        return new AcpSchema.InitializeResponse(
                AcpSchema.LATEST_PROTOCOL_VERSION,
                capabilities,
                List.of(),
                new AcpSchema.Implementation(
                        "java-hermes-agent-harness",
                        "0.1.0"
                ),
                null
        );
    }

    public AcpSchema.NewSessionResponse newSession(AcpSchema.NewSessionRequest request) {
        rejectUnsupportedSessionFeatures(
                request.mcpServers(),
                request.additionalDirectories()
        );
        Path cwd = resolveCwd(request.cwd());
        SessionId sessionId = new SessionId("acp-" + UUID.randomUUID());
        sessions.create(sessionId, "acp", null);
        sessions.configure(sessionId, cwd, defaultModel);
        return new AcpSchema.NewSessionResponse(sessionId.value(), null, null);
    }

    public AcpSchema.LoadSessionResponse loadSession(
            AcpSchema.LoadSessionRequest request,
            Consumer<AcpSchema.SessionUpdate> updates
    ) {
        rejectUnsupportedSessionFeatures(
                request.mcpServers(),
                request.additionalDirectories()
        );
        SessionId sessionId = requireSession(request.sessionId());
        updateCwd(sessionId, request.cwd());
        replayHistory(sessionId, updates);
        return new AcpSchema.LoadSessionResponse(null, null);
    }

    public AcpSchema.ResumeSessionResponse resumeSession(
            AcpSchema.ResumeSessionRequest request,
            Consumer<AcpSchema.SessionUpdate> updates
    ) {
        rejectUnsupportedSessionFeatures(
                request.mcpServers(),
                request.additionalDirectories()
        );
        SessionId sessionId = requireSession(request.sessionId());
        updateCwd(sessionId, request.cwd());
        replayHistory(sessionId, updates);
        return new AcpSchema.ResumeSessionResponse(null, null);
    }

    public AcpSchema.ForkSessionResponse forkSession(AcpSchema.ForkSessionRequest request) {
        rejectUnsupportedSessionFeatures(
                request.mcpServers(),
                request.additionalDirectories()
        );
        SessionId parent = requireSession(request.sessionId());
        SessionId child = new SessionId("acp-" + UUID.randomUUID());
        sessions.fork(parent, child, "acp");
        updateCwd(child, request.cwd());
        return new AcpSchema.ForkSessionResponse(child.value(), null, null);
    }

    public AcpSchema.ListSessionsResponse listSessions(AcpSchema.ListSessionsRequest request) {
        Path cwdFilter = request.cwd() == null || request.cwd().isBlank()
                ? null
                : resolveCwd(request.cwd());
        boolean afterCursor = request.cursor() == null || request.cursor().isBlank();
        List<AcpSchema.SessionInfo> matches = new ArrayList<>();
        String nextCursor = null;
        for (SessionLineage lineage : sessions.listLineages(1_000)) {
            if (!afterCursor) {
                if (lineage.sessionId().value().equals(request.cursor())) {
                    afterCursor = true;
                }
                continue;
            }
            SessionConfiguration configuration = sessions.configuration(lineage.sessionId())
                    .orElse(null);
            if (configuration == null
                    || cwdFilter != null && !configuration.workingDirectory().equals(cwdFilter)) {
                continue;
            }
            if (matches.size() == PAGE_SIZE) {
                nextCursor = matches.getLast().sessionId();
                break;
            }
            matches.add(new AcpSchema.SessionInfo(
                    lineage.sessionId().value(),
                    configuration.workingDirectory().toString()
            ));
        }
        return new AcpSchema.ListSessionsResponse(matches, nextCursor, null);
    }

    public AcpSchema.SetSessionConfigOptionResponse setSessionConfigOption(
            AcpSchema.SetSessionConfigOptionRequest request
    ) {
        if (!MODEL_CONFIG_ID.equals(request.configId())) {
            throw new IllegalArgumentException(
                    "unsupported ACP session config option: " + request.configId()
            );
        }
        SessionId sessionId = requireSession(request.sessionId());
        String model = request.value() == null ? "" : request.value().toString().trim();
        if (model.isBlank()) {
            throw new IllegalArgumentException("ACP model config must not be blank");
        }
        SessionConfiguration current = sessions.configuration(sessionId).orElseThrow(() ->
                new IllegalStateException("ACP session has no runtime configuration")
        );
        sessions.configure(sessionId, current.workingDirectory(), model);
        return new AcpSchema.SetSessionConfigOptionResponse(List.of(modelOption(model)));
    }

    public AcpSchema.PromptResponse prompt(
            AcpSchema.PromptRequest request,
            SyncPromptContext context
    ) {
        SessionId sessionId = requireSession(request.sessionId());
        SessionConfiguration configuration = sessions.configuration(sessionId).orElseThrow(() ->
                new IllegalStateException("ACP session has no runtime configuration")
        );
        String prompt = promptText(request);
        AgentRunResult result = runtime.run(AgentRunRequest.from(
                "acp",
                sessionId.value(),
                prompt,
                IterationBudget.maxTurns(8),
                Map.of(
                        "cwd", configuration.workingDirectory().toString(),
                        "model", configuration.model()
                )
        ));
        streamToolEvents(result, context);
        String message = result.finalAnswer().isBlank()
                ? "运行未完成：" + result.finishReason().name()
                : result.finalAnswer();
        context.sendMessage(message);
        return new AcpSchema.PromptResponse(stopReason(result.finishReason()));
    }

    public void cancel(AcpSchema.CancelNotification notification) {
        SessionId sessionId = requireSession(notification.sessionId());
        cancellation.request(sessionId.value());
    }

    private void replayHistory(
            SessionId sessionId,
            Consumer<AcpSchema.SessionUpdate> updates
    ) {
        Objects.requireNonNull(updates, "updates must not be null");
        for (AgentEvent event : sessions.load(sessionId).events()) {
            if (event.kind() == AgentEventKind.USER_MESSAGE) {
                updates.accept(new AcpSchema.UserMessageChunk(
                        "user_message_chunk",
                        new AcpSchema.TextContent(event.text())
                ));
            } else if (event.kind() == AgentEventKind.MODEL_FINAL_ANSWER) {
                updates.accept(new AcpSchema.AgentMessageChunk(
                        "agent_message_chunk",
                        new AcpSchema.TextContent(event.text())
                ));
            }
        }
    }

    private static void streamToolEvents(
            AgentRunResult result,
            SyncPromptContext context
    ) {
        Map<String, String> toolNames = new java.util.LinkedHashMap<>();
        for (AgentEvent event : result.state().events()) {
            if (event.kind() == AgentEventKind.TOOL_REQUESTED) {
                var request = event.toolRequest();
                toolNames.put(request.callId(), request.name());
                context.sendUpdate(context.getSessionId(), new AcpSchema.ToolCall(
                        "tool_call",
                        request.callId(),
                        request.name(),
                        toolKind(request.name()),
                        AcpSchema.ToolCallStatus.IN_PROGRESS,
                        List.of(),
                        List.of(),
                        request.arguments(),
                        null,
                        null
                ));
            } else if (event.kind() == AgentEventKind.TOOL_OBSERVED) {
                var observation = event.toolObservation();
                context.sendUpdate(context.getSessionId(), new AcpSchema.ToolCallUpdateNotification(
                        "tool_call_update",
                        observation.callId(),
                        toolNames.getOrDefault(observation.callId(), "tool"),
                        null,
                        observation.success()
                                ? AcpSchema.ToolCallStatus.COMPLETED
                                : AcpSchema.ToolCallStatus.FAILED,
                        List.of(new AcpSchema.ToolCallContentBlock(
                                "content",
                                new AcpSchema.TextContent(observation.content())
                        )),
                        List.of(),
                        null,
                        observation.content(),
                        null
                ));
            }
        }
    }

    private void updateCwd(SessionId sessionId, String configuredCwd) {
        SessionConfiguration current = sessions.configuration(sessionId).orElse(
                new SessionConfiguration(workspace, defaultModel)
        );
        sessions.configure(sessionId, resolveCwd(configuredCwd), current.model());
    }

    private SessionId requireSession(String rawSessionId) {
        SessionId sessionId = new SessionId(rawSessionId);
        if (sessions.lineage(sessionId).isEmpty()) {
            throw new IllegalArgumentException("ACP session does not exist: " + sessionId.value());
        }
        return sessionId;
    }

    private Path resolveCwd(String configured) {
        Path cwd = configured == null || configured.isBlank()
                ? workspace
                : Path.of(configured);
        if (!cwd.isAbsolute()) {
            cwd = workspace.resolve(cwd);
        }
        cwd = realDirectory(cwd);
        if (!cwd.startsWith(workspace)) {
            throw new IllegalArgumentException(
                    "ACP cwd must stay inside the configured workspace"
            );
        }
        return cwd;
    }

    private static Path realDirectory(Path directory) {
        try {
            Path real = directory.toRealPath();
            if (!Files.isDirectory(real)) {
                throw new IllegalArgumentException("ACP cwd must be an existing directory");
            }
            return real;
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("ACP cwd must be an existing directory", error);
        }
    }

    private static String promptText(AcpSchema.PromptRequest request) {
        if (request.prompt() == null || request.prompt().isEmpty()) {
            throw new IllegalArgumentException("ACP prompt must contain text");
        }
        List<String> text = request.prompt().stream()
                .filter(AcpSchema.TextContent.class::isInstance)
                .map(AcpSchema.TextContent.class::cast)
                .map(AcpSchema.TextContent::text)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        if (text.size() != request.prompt().size()) {
            throw new IllegalArgumentException(
                    "this Hermes runtime currently accepts ACP text blocks only"
            );
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("ACP prompt must contain text");
        }
        return String.join("\n", text);
    }

    private static AcpSchema.StopReason stopReason(FinishReason reason) {
        return switch (reason) {
            case FINAL_ANSWER -> AcpSchema.StopReason.END_TURN;
            case INTERRUPTED -> AcpSchema.StopReason.CANCELLED;
            case ITERATION_LIMIT -> AcpSchema.StopReason.MAX_TURN_REQUESTS;
            case ERROR_LIMIT -> AcpSchema.StopReason.REFUSAL;
        };
    }

    private static AcpSchema.ToolKind toolKind(String name) {
        return switch (name) {
            case "read_file", "list_directory" -> AcpSchema.ToolKind.READ;
            case "edit_file", "write_file", "patch" -> AcpSchema.ToolKind.EDIT;
            case "terminal", "execute_code" -> AcpSchema.ToolKind.EXECUTE;
            default -> AcpSchema.ToolKind.OTHER;
        };
    }

    private static AcpSchema.SessionConfigOption modelOption(String model) {
        return new AcpSchema.SessionConfigSelect(
                "select",
                MODEL_CONFIG_ID,
                "模型",
                "当前 ACP Session 使用的 OpenAI-compatible 模型",
                "model",
                model,
                List.of(new AcpSchema.SessionConfigSelectOption(model, model)),
                null
        );
    }

    private static void rejectUnsupportedSessionFeatures(
            List<AcpSchema.McpServer> mcpServers,
            List<String> additionalDirectories
    ) {
        if (mcpServers != null && !mcpServers.isEmpty()) {
            throw new IllegalArgumentException(
                    "session-scoped ACP MCP servers are not enabled in this runtime"
            );
        }
        if (additionalDirectories != null && !additionalDirectories.isEmpty()) {
            throw new IllegalArgumentException(
                    "additional ACP workspace roots are not enabled in this runtime"
            );
        }
    }
}
