package com.ading.ai.hermes.harness;

import com.ading.ai.hermes.checkpoint.FileWorkspaceCheckpointStore;
import com.ading.ai.hermes.context.reference.ContextReferenceResolver;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.ManualStopSignal;
import com.ading.ai.hermes.hook.HookFailureMode;
import com.ading.ai.hermes.hook.RuntimeHookChain;
import com.ading.ai.hermes.hook.RuntimeHookDecision;
import com.ading.ai.hermes.hook.RuntimeHookPoint;
import com.ading.ai.hermes.run.InMemoryRunCoordinator;
import com.ading.ai.hermes.run.RunStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHarnessTest {

    @TempDir
    Path workspace;

    @Test
    void preparesContextAndCheckpointBeforeCallingTheRuntime() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "before");
        AtomicReference<String> runtimeMessage = new AtomicReference<>();
        FileWorkspaceCheckpointStore checkpoints = new FileWorkspaceCheckpointStore(workspace);
        InMemoryRunCoordinator runs = new InMemoryRunCoordinator();
        AgentHarness harness = new AgentHarness(
                request -> {
                    runtimeMessage.set(request.userMessage());
                    try {
                        Files.writeString(workspace.resolve("README.md"), "after");
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                    return new AgentRunResult(FinishReason.FINAL_ANSWER, "done", AgentState.start("task"));
                },
                new ContextReferenceResolver(workspace, 10_000, url -> "", git -> ""),
                checkpoints,
                runs,
                RuntimeHookChain.empty()
        );

        HarnessRunResult result = harness.run(new HarnessRunRequest(
                AgentRunRequest.from("cli", "session-1", "review @file:README.md",
                        IterationBudget.maxTurns(3), Map.of()),
                List.of("README.md")
        ));

        assertEquals(HarnessRunStatus.COMPLETED, result.status());
        assertTrue(runtimeMessage.get().contains("--- Attached Context ---"));
        assertEquals("before", checkpoints.rollback(result.checkpoint().orElseThrow().id())
                .restoredPaths().contains("README.md") ? Files.readString(workspace.resolve("README.md")) : "");
        assertEquals(RunStatus.COMPLETED, runs.snapshot(result.runId()).status());
    }

    @Test
    void failClosedBeforeRunHookPreventsTheRuntimeCall() {
        AtomicBoolean called = new AtomicBoolean();
        RuntimeHookChain hooks = RuntimeHookChain.empty().register(
                "tenant-policy",
                RuntimeHookPoint.BEFORE_RUN,
                10,
                HookFailureMode.FAIL_CLOSED,
                event -> RuntimeHookDecision.block("tenant blocked", event.payload())
        );
        AgentHarness harness = new AgentHarness(
                request -> {
                    called.set(true);
                    return new AgentRunResult(FinishReason.FINAL_ANSWER, "done", AgentState.start("task"));
                },
                new ContextReferenceResolver(workspace, 10_000, url -> "", git -> ""),
                new FileWorkspaceCheckpointStore(workspace),
                new InMemoryRunCoordinator(),
                hooks
        );

        HarnessRunResult result = harness.run(new HarnessRunRequest(
                AgentRunRequest.start("blocked task", IterationBudget.maxTurns(1)),
                List.of()
        ));

        assertEquals(HarnessRunStatus.BLOCKED, result.status());
        assertFalse(called.get());
        assertTrue(result.message().contains("tenant blocked"));
    }

    @Test
    void assignsOneSessionIdentityBeforeCallingRuntime() {
        AtomicReference<String> runtimeSession = new AtomicReference<>();
        InMemoryRunCoordinator runs = new InMemoryRunCoordinator();
        AgentHarness harness = new AgentHarness(
                request -> {
                    runtimeSession.set(request.conversationId());
                    return new AgentRunResult(
                            FinishReason.FINAL_ANSWER,
                            "done",
                            AgentState.start(request.userMessage())
                    );
                },
                new ContextReferenceResolver(workspace, 10_000, url -> "", git -> ""),
                new FileWorkspaceCheckpointStore(workspace),
                runs,
                RuntimeHookChain.empty()
        );

        HarnessRunResult result = harness.run(new HarnessRunRequest(
                AgentRunRequest.start("task", IterationBudget.maxTurns(1)),
                List.of()
        ));

        String coordinatedSession = runs.snapshot(result.runId()).sessionId();
        assertFalse(runtimeSession.get().isBlank());
        assertEquals(coordinatedSession, runtimeSession.get());
    }

    @Test
    void marksBudgetExhaustionAsFailedInsteadOfCompleted() {
        assertFailedFinishReason(FinishReason.ITERATION_LIMIT);
    }

    @Test
    void marksRecoveryExhaustionAsFailedInsteadOfCompleted() {
        assertFailedFinishReason(FinishReason.ERROR_LIMIT);
    }

    @Test
    void propagatesAnExternalParentStopSignalIntoTheRuntime() {
        ManualStopSignal parentStop = new ManualStopSignal();
        parentStop.requestStop("parent run stopped");
        AgentHarness harness = new AgentHarness(
                (HarnessRuntime) (request, stopSignal) -> new AgentRunResult(
                        stopSignal.stopRequested() ? FinishReason.INTERRUPTED : FinishReason.FINAL_ANSWER,
                        "",
                        AgentState.start(request.userMessage())
                ),
                new ContextReferenceResolver(workspace, 10_000, url -> "", git -> ""),
                new FileWorkspaceCheckpointStore(workspace),
                new InMemoryRunCoordinator(),
                RuntimeHookChain.empty()
        );

        HarnessRunResult result = harness.run(new HarnessRunRequest(
                AgentRunRequest.start("child task", IterationBudget.maxTurns(1)),
                List.of()
        ), parentStop);

        assertEquals(HarnessRunStatus.INTERRUPTED, result.status());
        assertEquals("parent run stopped", result.message());
    }

    private void assertFailedFinishReason(FinishReason finishReason) {
        InMemoryRunCoordinator runs = new InMemoryRunCoordinator();
        AgentHarness harness = new AgentHarness(
                request -> new AgentRunResult(
                        finishReason,
                        "",
                        AgentState.start(request.userMessage())
                ),
                new ContextReferenceResolver(workspace, 10_000, url -> "", git -> ""),
                new FileWorkspaceCheckpointStore(workspace),
                runs,
                RuntimeHookChain.empty()
        );

        HarnessRunResult result = harness.run(new HarnessRunRequest(
                AgentRunRequest.from(
                        "cli",
                        "failed-session-" + finishReason.name().toLowerCase(),
                        "task",
                        IterationBudget.maxTurns(1),
                        Map.of()
                ),
                List.of()
        ));

        assertEquals(HarnessRunStatus.FAILED, result.status());
        assertEquals(finishReason, result.agentResult().orElseThrow().finishReason());
        assertEquals(RunStatus.FAILED, runs.snapshot(result.runId()).status());
        assertTrue(result.message().contains(finishReason.name()));
    }
}
