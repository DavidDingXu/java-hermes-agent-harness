package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.ModelTurn;
import com.ading.ai.hermes.gateway.feishu.FeishuEvent;
import com.ading.ai.hermes.gateway.feishu.FeishuHandleResult;
import com.ading.ai.hermes.gateway.local.FeishuLocalService;
import com.ading.ai.hermes.model.ChatResponse;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.Usage;
import com.ading.ai.hermes.memory.MemoryTarget;
import com.ading.ai.hermes.harness.HarnessRunRequest;
import com.ading.ai.hermes.harness.HarnessRunStatus;
import com.ading.ai.hermes.run.BusyInputMode;
import com.ading.ai.hermes.run.RunStatus;
import com.ading.ai.hermes.session.SessionId;
import com.ading.ai.hermes.skill.SkillManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermesRuntimeFactoryTest {

    @TempDir
    Path workspace;

    @Test
    void routesDirectAndRegisteredFeishuEntriesThroughOneHarnessRuntime() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "workspace-evidence");
        AtomicReference<String> modelPrompt = new AtomicReference<>();
        List<String> replies = new ArrayList<>();
        HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                workspace,
                request -> {
                    modelPrompt.set(request.messages().toString());
                    return ChatResponse.of(ModelTurn.finalAnswer("done"));
                },
                new ModelOptions("test-model", 0.0),
                reply -> replies.add(reply.text())
        );

        var direct = assembly.runtime().run(AgentRunRequest.from(
                "cli",
                "cli-session",
                "inspect @file:README.md",
                IterationBudget.maxTurns(2),
                Map.of()
        ));
        assertTrue(modelPrompt.get().contains("workspace-evidence"));
        FeishuHandleResult feishu = assembly.localServices().invoke(
                FeishuLocalService.SERVICE_NAME,
                FeishuEvent.text("evt-1", "chat-1", "user-1", "say hello"),
                FeishuHandleResult.class
        );

        assertEquals("done", direct.finalAnswer());
        assertEquals(List.of("done"), replies);
        assertEquals("PROCESSED", feishu.status().name());
        assertTrue(assembly.runs().activeRunForSession("cli-session").isEmpty());
        assertTrue(assembly.runs().activeRunForSession("chat-1").isEmpty());
    }

    @Test
    void disablesUrlReferencesOnTheDefaultCliAndLocalServiceRuntime() {
        AtomicReference<String> modelPrompt = new AtomicReference<>();
        HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                workspace,
                request -> {
                    modelPrompt.set(request.messages().toString());
                    return ChatResponse.of(ModelTurn.finalAnswer("done"));
                },
                new ModelOptions("test-model", 0.0),
                reply -> { }
        );

        assembly.runtime().run(AgentRunRequest.from(
                "cli", "session", "inspect @url:https://example.com",
                IterationBudget.maxTurns(1), Map.of()
        ));

        assertTrue(modelPrompt.get().contains("URL references are disabled"));
    }

    @Test
    void appliesConfiguredMemoryMatchingSkillsAndToolPermissions() {
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicReference<List<String>> toolNames = new AtomicReference<>();
        SkillManifest matching = new SkillManifest(
                "reader-summary",
                "",
                "1.0.0",
                true,
                List.of("summarize"),
                "Answer with exactly one sentence."
        );
        SkillManifest unrelated = new SkillManifest(
                "java-testing",
                "",
                "1.0.0",
                true,
                List.of("test"),
                "Run project verification."
        );
        HermesRuntimeOptions options = new HermesRuntimeOptions(
                20_000,
                50_000,
                false,
                "Reply in Chinese.",
                "Project uses Java 21.",
                "User prefers the conclusion first.",
                List.of(matching, unrelated)
        );
        HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                workspace,
                request -> {
                    systemPrompt.set(request.messages().getFirst().content());
                    toolNames.set(request.tools().stream().map(tool -> tool.name()).toList());
                    return ChatResponse.of(ModelTurn.finalAnswer("done"));
                },
                new ModelOptions("test-model", 0.0),
                reply -> { },
                options
        );

        assembly.runtime().run(AgentRunRequest.from(
                "web", "session", "summarize README",
                IterationBudget.maxTurns(1), Map.of()
        ));

        assertTrue(systemPrompt.get().contains("Reply in Chinese."));
        assertTrue(systemPrompt.get().contains("Project uses Java 21."));
        assertTrue(systemPrompt.get().contains("User prefers the conclusion first."));
        assertTrue(systemPrompt.get().contains("Answer with exactly one sentence."));
        assertTrue(!systemPrompt.get().contains("Run project verification."));
        assertEquals(List.of("read_file", "list_directory"), toolNames.get());
    }

    @Test
    void connectsRunCoordinatorInterruptToTheDefaultRuntimeStopSignal() throws Exception {
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                workspace,
                request -> {
                    providerStarted.countDown();
                    try {
                        releaseProvider.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    return ChatResponse.of(ModelTurn.finalAnswer("too late"));
                },
                new ModelOptions("test-model", 0.0),
                reply -> { }
        );

        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> assembly.harness().run(new HarnessRunRequest(
                    AgentRunRequest.from(
                            "cli", "interrupt-session", "long task",
                            IterationBudget.maxTurns(2), Map.of()
                    ),
                    List.of()
            )));
            assertTrue(providerStarted.await(5, TimeUnit.SECONDS));
            var active = assembly.runs().activeRunForSession("interrupt-session").orElseThrow();
            assembly.runs().submitBusyInput(active.runId(), "stop now", BusyInputMode.INTERRUPT);
            releaseProvider.countDown();

            var result = future.get(5, TimeUnit.SECONDS);
            assertEquals(HarnessRunStatus.INTERRUPTED, result.status());
            assertEquals(RunStatus.STOPPED, assembly.runs().snapshot(result.runId()).status());
        }
    }

    @Test
    void persistsRunEvidenceAndReusesApprovedMemoryAfterRuntimeRestart() {
        HermesRuntimeAssembly first = HermesRuntimeFactory.create(
                workspace,
                request -> new ChatResponse(
                        ModelTurn.finalAnswer("记住了"),
                        new Usage(12, 4),
                        "scripted-provider"
                ),
                new ModelOptions("test-model", 0.0),
                reply -> { }
        );

        first.runtime().run(AgentRunRequest.from(
                "web",
                "reader-session",
                "希望你先给结论，再解释原因",
                IterationBudget.maxTurns(2),
                Map.of()
        ));

        assertEquals(2, first.sessions().load(new SessionId("reader-session")).events().size());
        assertEquals(1, first.trajectories().records().size());
        assertEquals(1, first.metrics().calls().size());
        assertEquals(12, first.metrics().calls().getFirst().usage().inputTokens());
        assertEquals(
                List.of("User prefers answers that give the conclusion first, then explain the reason."),
                first.memories().entries(MemoryTarget.USER)
        );

        AtomicReference<String> restartedPrompt = new AtomicReference<>();
        HermesRuntimeAssembly restarted = HermesRuntimeFactory.create(
                workspace,
                request -> {
                    restartedPrompt.set(request.messages().getFirst().content());
                    return ChatResponse.of(ModelTurn.finalAnswer("done"));
                },
                new ModelOptions("test-model", 0.0),
                reply -> { }
        );
        restarted.runtime().run(AgentRunRequest.from(
                "web",
                "next-session",
                "summarize README",
                IterationBudget.maxTurns(1),
                Map.of()
        ));

        assertTrue(restartedPrompt.get().contains(
                "User prefers answers that give the conclusion first, then explain the reason."
        ));
        assertEquals(2, restarted.trajectories().records().size());
    }

    @Test
    void appliesWorkspaceGuardrailsBeforeToolExecution() {
        AtomicInteger calls = new AtomicInteger();
        HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                workspace,
                request -> {
                    int call = calls.getAndIncrement();
                    if (call == 0) {
                        return ChatResponse.of(ModelTurn.toolRequest(new com.ading.ai.hermes.core.ToolRequest(
                                "call-guard",
                                "read_file",
                                Map.of("path", "../secret.txt")
                        )));
                    }
                    return ChatResponse.of(ModelTurn.finalAnswer("blocked safely"));
                },
                new ModelOptions("test-model", 0.0),
                reply -> { }
        );

        var result = assembly.runtime().run(AgentRunRequest.from(
                "web", "guarded-session", "inspect outside file",
                IterationBudget.maxTurns(3), Map.of()
        ));

        assertTrue(result.state().events().stream()
                .filter(event -> event.toolObservation() != null)
                .anyMatch(event -> event.toolObservation().content().contains(
                        "tool request blocked: path must stay inside the workspace"
                )));
    }

    @Test
    void recoversOneProviderFailureAndPersistsOnlyRedactedEvidence() {
        AtomicInteger calls = new AtomicInteger();
        HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                workspace,
                request -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new IllegalStateException("temporary provider failure token=sk-runtime-secret");
                    }
                    return ChatResponse.of(ModelTurn.finalAnswer("recovered"));
                },
                new ModelOptions("test-model", 0.0),
                reply -> { }
        );

        var result = assembly.runtime().run(AgentRunRequest.from(
                "web",
                "recovery-session",
                "retry this request with token sk-user-secret",
                IterationBudget.maxTurns(3),
                Map.of()
        ));

        assertEquals("recovered", result.finalAnswer());
        assertTrue(result.state().events().stream()
                .anyMatch(event -> event.kind() == com.ading.ai.hermes.core.AgentEventKind.ERROR_RECOVERED));
        assertEquals(2, assembly.metrics().calls().size());
        assertEquals(3, assembly.sessions().load(new SessionId("recovery-session")).events().size());
        String persistedSession = assembly.sessions().load(new SessionId("recovery-session"))
                .events().toString();
        String persistedTrajectory = assembly.trajectories().records().toString();
        assertFalse(persistedSession.contains("sk-user-secret"));
        assertFalse(persistedSession.contains("sk-runtime-secret"));
        assertFalse(persistedTrajectory.contains("sk-user-secret"));
        assertFalse(persistedTrajectory.contains("sk-runtime-secret"));
    }
}
