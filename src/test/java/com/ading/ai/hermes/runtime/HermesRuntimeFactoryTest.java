package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.ModelTurn;
import com.ading.ai.hermes.gateway.feishu.FeishuEvent;
import com.ading.ai.hermes.gateway.feishu.FeishuHandleResult;
import com.ading.ai.hermes.gateway.local.FeishuLocalService;
import com.ading.ai.hermes.model.ChatResponse;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.harness.HarnessRunRequest;
import com.ading.ai.hermes.harness.HarnessRunStatus;
import com.ading.ai.hermes.run.BusyInputMode;
import com.ading.ai.hermes.run.RunStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
