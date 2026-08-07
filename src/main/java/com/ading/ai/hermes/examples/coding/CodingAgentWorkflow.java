package com.ading.ai.hermes.examples.coding;

import com.ading.ai.hermes.context.ContextFile;
import com.ading.ai.hermes.context.ContextFileCollector;
import com.ading.ai.hermes.context.ContextFileSet;
import com.ading.ai.hermes.model.ChatMessage;
import com.ading.ai.hermes.model.ChatRequest;
import com.ading.ai.hermes.model.ChatRole;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.ModelProvider;
import com.ading.ai.hermes.observability.TraceEvent;
import com.ading.ai.hermes.observability.TraceEventKind;
import com.ading.ai.hermes.observability.TrajectoryRecord;
import com.ading.ai.hermes.tools.basic.UniqueTextEdit;
import com.ading.ai.hermes.workspace.WorkspacePathPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CodingAgentWorkflow {

    private final Path workspaceRoot;
    private final WorkspacePathPolicy pathPolicy;
    private final ModelProvider modelProvider;
    private final VerificationRunner verificationRunner;
    private final ObjectMapper objectMapper;
    private final CodingAgentPolicy policy;
    private final ModelOptions modelOptions;
    private final Clock clock;

    public CodingAgentWorkflow(
            Path workspaceRoot,
            ModelProvider modelProvider,
            VerificationRunner verificationRunner,
            ObjectMapper objectMapper,
            CodingAgentPolicy policy
    ) {
        this(
                workspaceRoot,
                modelProvider,
                verificationRunner,
                objectMapper,
                policy,
                new ModelOptions("coding-agent-model", 0.0),
                Clock.systemUTC()
        );
    }

    public CodingAgentWorkflow(
            Path workspaceRoot,
            ModelProvider modelProvider,
            VerificationRunner verificationRunner,
            ObjectMapper objectMapper,
            CodingAgentPolicy policy,
            ModelOptions modelOptions
    ) {
        this(workspaceRoot, modelProvider, verificationRunner, objectMapper, policy, modelOptions, Clock.systemUTC());
    }

    public CodingAgentWorkflow(
            Path workspaceRoot,
            ModelProvider modelProvider,
            VerificationRunner verificationRunner,
            ObjectMapper objectMapper,
            CodingAgentPolicy policy,
            ModelOptions modelOptions,
            Clock clock
    ) {
        this.pathPolicy = new WorkspacePathPolicy(workspaceRoot);
        this.workspaceRoot = pathPolicy.root();
        this.modelProvider = Objects.requireNonNull(modelProvider, "modelProvider must not be null");
        this.verificationRunner = Objects.requireNonNull(verificationRunner, "verificationRunner must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.modelOptions = Objects.requireNonNull(modelOptions, "modelOptions must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CodingAgentRunResult run(CodingTask task) {
        Objects.requireNonNull(task, "task must not be null");
        String timestamp = now();
        List<TraceEvent> events = new ArrayList<>();
        events.add(event(TraceEventKind.USER_MESSAGE, timestamp, Map.of("text", task.goal())));

        ContextFileSet context = new ContextFileCollector(workspaceRoot, policy.maxContextCharsPerFile())
                .collect(task.contextPaths());
        if (!context.rejections().isEmpty()) {
            String message = "context file rejected: " + context.rejections().get(0).reason();
            events.add(toolObserved(timestamp, "context", false, message));
            return failure(message, Optional.empty(), List.of(), events, timestamp);
        }

        CodingPlan plan;
        try {
            String prompt = prompt(task, context.files());
            String rawPlan = modelProvider.complete(chatRequest(prompt)).turn().finalAnswer();
            plan = objectMapper.readValue(rawPlan, CodingPlan.class);
        } catch (RuntimeException | IOException error) {
            String message = "failed to parse coding plan";
            events.add(toolObserved(timestamp, "model_plan", false, message));
            return failure(message, Optional.empty(), List.of(), events, timestamp);
        }

        for (String command : plan.verificationCommands()) {
            if (!policy.allowsVerificationCommand(command)) {
                String message = "verification command is not allowed: " + command;
                events.add(toolObserved(timestamp, "verify", false, message));
                return failure(message, Optional.of(plan), List.of(), events, timestamp);
            }
        }

        for (CodingPatch patch : plan.patches()) {
            events.add(toolRequested(timestamp, "apply_patch", Map.of("path", patch.path())));
            String patchError = applyPatch(patch);
            if (!patchError.isBlank()) {
                events.add(toolObserved(timestamp, "apply_patch", false, patchError));
                return failure(patchError, Optional.of(plan), List.of(), events, timestamp);
            }
            events.add(toolObserved(timestamp, "apply_patch", true, "patch applied: " + patch.path()));
        }

        List<VerificationResult> verificationResults = new ArrayList<>();
        for (String command : plan.verificationCommands()) {
            events.add(toolRequested(timestamp, "verify", Map.of("command", command)));
            VerificationResult result = verificationRunner.run(command, workspaceRoot);
            verificationResults.add(result);
            events.add(toolObserved(timestamp, "verify", result.success(), result.output()));
            if (!result.success()) {
                return failure("verification failed: " + command, Optional.of(plan), verificationResults, events, timestamp);
            }
        }

        events.add(new TraceEvent(TraceEventKind.RUN_FINISHED, "coding-agent", "turn-1", "", "", timestamp, Map.of(
                "finishReason", "FINAL_ANSWER",
                "summary", plan.summary()
        )));
        return new CodingAgentRunResult(
                true,
                plan.summary(),
                Optional.of(plan),
                verificationResults,
                new TrajectoryRecord("coding-agent", "turn-1", timestamp, events)
        );
    }

    private String applyPatch(CodingPatch patch) {
        Path target = resolvePath(patch.path());
        if (target == null) {
            return "patch path escapes workspace";
        }
        if (!Files.exists(target)) {
            return "patch target not found: " + patch.path();
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            var edit = UniqueTextEdit.apply(content, patch.expected(), patch.replacement());
            if (!edit.success()) {
                return edit.error() + ": " + patch.path();
            }
            Files.writeString(target, edit.content(), StandardCharsets.UTF_8);
            return "";
        } catch (IOException error) {
            return "failed to patch file: " + patch.path();
        }
    }

    private Path resolvePath(String rawPath) {
        try {
            return pathPolicy.resolveExisting(rawPath);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private ChatRequest chatRequest(String prompt) {
        return new ChatRequest(
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "Return a JSON coding plan with summary, patches and verificationCommands."),
                        new ChatMessage(ChatRole.USER, prompt)
                ),
                List.of(),
                modelOptions
        );
    }

    private String prompt(CodingTask task, List<ContextFile> files) {
        StringBuilder text = new StringBuilder();
        text.append("Goal:\n").append(task.goal()).append("\n\n");
        text.append("Plan format:\n");
        text.append("""
                {
                  "summary": "short summary",
                  "patches": [
                    {
                      "path": "relative file path",
                      "expected": "exact text to replace",
                      "replacement": "replacement text"
                    }
                  ],
                  "verificationCommands": ["mvn -Dtest=SomeTest test"]
                }
                """);
        text.append("\nRules:\n");
        text.append("- Return JSON only, with no Markdown fences.\n");
        text.append("- Use only context file paths listed below.\n");
        text.append("- Use verification commands starting with: ")
                .append(policy.allowedVerificationPrefixes())
                .append("\n\n");
        text.append("Context:\n");
        for (ContextFile file : files) {
            text.append("Path: ").append(file.path()).append("\n");
            text.append(file.content()).append("\n\n");
        }
        return text.toString();
    }

    private CodingAgentRunResult failure(
            String message,
            Optional<CodingPlan> plan,
            List<VerificationResult> verificationResults,
            List<TraceEvent> events,
            String timestamp
    ) {
        events.add(new TraceEvent(TraceEventKind.RUN_FINISHED, "coding-agent", "turn-1", "", "", timestamp, Map.of(
                "finishReason", "ERROR_LIMIT_REACHED",
                "summary", message
        )));
        return new CodingAgentRunResult(
                false,
                message,
                plan,
                verificationResults,
                new TrajectoryRecord("coding-agent", "turn-1", timestamp, events)
        );
    }

    private TraceEvent toolRequested(String timestamp, String toolName, Map<String, String> arguments) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("callId", toolName + "-1");
        attrs.put("toolName", toolName);
        attrs.put("arguments", String.valueOf(arguments));
        return event(TraceEventKind.TOOL_REQUESTED, timestamp, attrs);
    }

    private TraceEvent toolObserved(String timestamp, String toolName, boolean success, String content) {
        return event(TraceEventKind.TOOL_OBSERVED, timestamp, Map.of(
                "callId", toolName + "-1",
                "success", String.valueOf(success),
                "content", content
        ));
    }

    private TraceEvent event(TraceEventKind kind, String timestamp, Map<String, String> attributes) {
        return new TraceEvent(kind, "coding-agent", "turn-1", "", "", timestamp, attributes);
    }

    private String now() {
        Instant instant = clock.instant();
        return instant.toString();
    }

    private static Path toRealDirectory(Path workspaceRoot) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        try {
            Path realRoot = workspaceRoot.toRealPath();
            if (!Files.isDirectory(realRoot)) {
                throw new IllegalArgumentException("workspaceRoot must be a directory");
            }
            return realRoot;
        } catch (IOException error) {
            throw new IllegalArgumentException("workspaceRoot must exist", error);
        }
    }
}
