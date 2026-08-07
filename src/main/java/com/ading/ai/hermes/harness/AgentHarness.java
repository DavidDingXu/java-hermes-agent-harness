package com.ading.ai.hermes.harness;

import com.ading.ai.hermes.checkpoint.WorkspaceCheckpoint;
import com.ading.ai.hermes.checkpoint.WorkspaceCheckpointStore;
import com.ading.ai.hermes.context.reference.ContextReferenceResolver;
import com.ading.ai.hermes.context.reference.ContextReferenceResult;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.StopSignal;
import com.ading.ai.hermes.hook.RuntimeHookChain;
import com.ading.ai.hermes.hook.RuntimeHookDecision;
import com.ading.ai.hermes.hook.RuntimeHookEvent;
import com.ading.ai.hermes.hook.RuntimeHookPoint;
import com.ading.ai.hermes.run.RunCoordinator;
import com.ading.ai.hermes.run.RunEventType;
import com.ading.ai.hermes.run.RunSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AgentHarness {

    private final HarnessRuntime runtime;
    private final ContextReferenceResolver contextReferences;
    private final WorkspaceCheckpointStore checkpoints;
    private final RunCoordinator runs;
    private final RuntimeHookChain hooks;

    public AgentHarness(
            AgentRuntime runtime,
            ContextReferenceResolver contextReferences,
            WorkspaceCheckpointStore checkpoints,
            RunCoordinator runs,
            RuntimeHookChain hooks
    ) {
        this(adaptRuntime(runtime), contextReferences, checkpoints, runs, hooks);
    }

    public AgentHarness(
            HarnessRuntime runtime,
            ContextReferenceResolver contextReferences,
            WorkspaceCheckpointStore checkpoints,
            RunCoordinator runs,
            RuntimeHookChain hooks
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.contextReferences = Objects.requireNonNull(
                contextReferences, "contextReferences must not be null"
        );
        this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints must not be null");
        this.runs = Objects.requireNonNull(runs, "runs must not be null");
        this.hooks = Objects.requireNonNull(hooks, "hooks must not be null");
    }

    public HarnessRunResult run(HarnessRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AgentRunRequest agentRequest = request.agentRequest();
        String sessionId = agentRequest.conversationId().isBlank()
                ? agentRequest.source() + "-" + UUID.randomUUID()
                : agentRequest.conversationId();
        RunSnapshot run = runs.start(sessionId, agentRequest.userMessage());
        List<String> warnings = new ArrayList<>();
        Optional<WorkspaceCheckpoint> checkpoint = Optional.empty();

        try {
            ContextReferenceResult context = contextReferences.resolve(agentRequest.userMessage());
            warnings.addAll(context.warnings());
            if (context.blocked()) {
                String reason = "context reference injection exceeded its safety limit";
                runs.fail(run.runId(), reason);
                return result(run.runId(), HarnessRunStatus.BLOCKED, reason,
                        Optional.empty(), checkpoint, warnings);
            }

            RuntimeHookDecision beforeRun = hooks.invoke(new RuntimeHookEvent(
                    RuntimeHookPoint.BEFORE_RUN,
                    run.runId(),
                    sessionId,
                    Map.of("message", context.resolvedMessage())
            ));
            warnings.addAll(beforeRun.warnings());
            if (!beforeRun.allowed()) {
                runs.fail(run.runId(), beforeRun.reason());
                return result(run.runId(), HarnessRunStatus.BLOCKED, beforeRun.reason(),
                        Optional.empty(), checkpoint, warnings);
            }

            String resolvedMessage = beforeRun.payload().getOrDefault(
                    "message", context.resolvedMessage()
            ).toString();
            if (!request.checkpointPaths().isEmpty()) {
                checkpoint = Optional.of(checkpoints.capture(request.checkpointPaths()));
            }

            runs.emit(run.runId(), RunEventType.MODEL_STARTED, "agent runtime started");
            AgentRunResult agentResult = runtime.run(AgentRunRequest.from(
                    agentRequest.source(),
                    sessionId,
                    resolvedMessage,
                    agentRequest.budget(),
                    agentRequest.metadata()
            ), stopSignal(run.runId()));
            runs.emit(run.runId(), RunEventType.MODEL_FINISHED, agentResult.finishReason().name());

            if (agentResult.finishReason() == FinishReason.INTERRUPTED) {
                String reason = stopSignal(run.runId()).reason();
                runs.stop(run.runId(), reason);
                return result(run.runId(), HarnessRunStatus.INTERRUPTED, reason,
                        Optional.of(agentResult), checkpoint, warnings);
            }

            RuntimeHookDecision afterRun = hooks.invoke(new RuntimeHookEvent(
                    RuntimeHookPoint.AFTER_RUN,
                    run.runId(),
                    sessionId,
                    Map.of("answer", agentResult.finalAnswer(), "finishReason", agentResult.finishReason().name())
            ));
            warnings.addAll(afterRun.warnings());
            if (!afterRun.allowed()) {
                runs.fail(run.runId(), afterRun.reason());
                return result(run.runId(), HarnessRunStatus.BLOCKED, afterRun.reason(),
                        Optional.of(agentResult), checkpoint, warnings);
            }

            String answer = afterRun.payload().getOrDefault("answer", agentResult.finalAnswer()).toString();
            runs.complete(run.runId(), answer);
            return result(run.runId(), HarnessRunStatus.COMPLETED, answer,
                    Optional.of(agentResult), checkpoint, warnings);
        } catch (Exception exception) {
            String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            if (runs.snapshot(run.runId()).stopRequested()) {
                runs.stop(run.runId(), message);
                return result(run.runId(), HarnessRunStatus.INTERRUPTED, message,
                        Optional.empty(), checkpoint, warnings);
            }
            runs.fail(run.runId(), message);
            return result(run.runId(), HarnessRunStatus.FAILED, message,
                    Optional.empty(), checkpoint, warnings);
        }
    }

    private StopSignal stopSignal(String runId) {
        return new StopSignal() {
            @Override
            public boolean stopRequested() {
                return runs.snapshot(runId).stopRequested();
            }

            @Override
            public String reason() {
                return runs.eventsAfter(runId, 0).stream()
                        .filter(event -> event.type() == RunEventType.STOP_REQUESTED)
                        .reduce((left, right) -> right)
                        .map(event -> event.payload())
                        .orElse("stop requested");
            }
        };
    }

    private static HarnessRuntime adaptRuntime(AgentRuntime runtime) {
        AgentRuntime requiredRuntime = Objects.requireNonNull(runtime, "runtime must not be null");
        return (request, stopSignal) -> requiredRuntime.run(request);
    }

    private static HarnessRunResult result(
            String runId,
            HarnessRunStatus status,
            String message,
            Optional<AgentRunResult> agentResult,
            Optional<WorkspaceCheckpoint> checkpoint,
            List<String> warnings
    ) {
        return new HarnessRunResult(runId, status, message, agentResult, checkpoint, warnings);
    }
}
