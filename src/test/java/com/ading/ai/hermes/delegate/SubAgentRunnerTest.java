package com.ading.ai.hermes.delegate;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.CancellableAgentRuntime;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.ManualStopSignal;
import com.ading.ai.hermes.core.StopSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubAgentRunnerTest {

    @Test
    void runsEachDelegatedTaskWithFocusedContextAndOwnBudget() {
        AtomicInteger runCount = new AtomicInteger();
        AgentRuntime runtime = request -> {
            int index = runCount.incrementAndGet();
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "summary-" + index + ": " + request.userMessage(),
                    new AgentState(List.of(
                            AgentEvent.userMessage(request.userMessage()),
                            AgentEvent.modelFinalAnswer("child internal answer " + index)
                    ), 1)
            );
        };
        SubAgentRunner runner = new SubAgentRunner(runtime, new DelegationPolicy(3, IterationBudget.maxTurns(5)));
        DelegationRequest request = new DelegationRequest(List.of(
                new SubAgentTask("task-1", "inspect payment tests", "path: payments", List.of("file"), IterationBudget.maxTurns(2)),
                new SubAgentTask("task-2", "inspect order tests", "path: orders", List.of("file"), null)
        ));

        DelegationResult result = runner.run(request);

        assertEquals(2, result.results().size());
        assertEquals(DelegationStatus.COMPLETED, result.results().get(0).status());
        assertEquals("summary-1: inspect payment tests\n\nContext:\npath: payments", result.results().get(0).summary());
        assertEquals(2, result.results().get(0).budget().maxTurns());
        assertEquals(5, result.results().get(1).budget().maxTurns());
    }

    @Test
    void returnsOnlyChildSummaryWithoutLeakingIntermediateEvents() {
        AgentRuntime runtime = request -> new AgentRunResult(
                FinishReason.FINAL_ANSWER,
                "finished child task",
                new AgentState(List.of(
                        AgentEvent.userMessage(request.userMessage()),
                        AgentEvent.toolRequested(new com.ading.ai.hermes.core.ToolRequest("call-1", "read_file", Map.of("path", "secret.log"))),
                        AgentEvent.toolObserved(com.ading.ai.hermes.core.ToolObservation.success("call-1", "large internal output")),
                        AgentEvent.modelFinalAnswer("finished child task")
                ), 2)
        );
        SubAgentRunner runner = new SubAgentRunner(runtime, new DelegationPolicy(2, IterationBudget.maxTurns(4)));

        DelegationResult result = runner.run(new DelegationRequest(List.of(
                new SubAgentTask("task-1", "read file", "", List.of("file"), null)
        )));

        SubAgentResult child = result.results().get(0);
        assertEquals("finished child task", child.summary());
        assertEquals(FinishReason.FINAL_ANSWER, child.finishReason());
        assertEquals(2, child.turnsUsed());
    }

    @Test
    void createsAnIsolatedConversationAndPassesToolsetsAsRuntimePolicy() {
        List<AgentRunRequest> captured = new ArrayList<>();
        AtomicReference<StopSignal> capturedStop = new AtomicReference<>();
        CancellableAgentRuntime runtime = (request, stopSignal) -> {
            captured.add(request);
            capturedStop.set(stopSignal);
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "done",
                    AgentState.start(request.userMessage()).incrementTurns()
            );
        };
        SubAgentRunner runner = new SubAgentRunner(
                runtime,
                new DelegationPolicy(2, IterationBudget.maxTurns(4))
        );
        ManualStopSignal parentStop = new ManualStopSignal();
        DelegationRequest request = new DelegationRequest(
                "delegation-7",
                "parent-run-3",
                "parent-conversation",
                List.of(
                        new SubAgentTask(
                                "task-1", "inspect code", "", List.of("workspace-read"), null
                        ),
                        new SubAgentTask(
                                "task-2", "summarize code", "", List.of("workspace-read"), null
                        )
                )
        );

        DelegationResult result = runner.run(request, parentStop);

        assertEquals("subagent", captured.getFirst().source());
        assertEquals(
                "parent-conversation.subagent.delegation-7.task-1",
                captured.getFirst().conversationId()
        );
        assertEquals("parent-run-3", captured.getFirst().metadata().get(SubAgentMetadata.PARENT_RUN_ID));
        assertEquals("workspace-read", captured.getFirst().metadata().get(SubAgentMetadata.TOOLSETS));
        assertEquals(parentStop, capturedStop.get());
        assertEquals(captured.getFirst().conversationId(), result.results().getFirst().conversationId());
        assertEquals(2, captured.stream().map(AgentRunRequest::conversationId).distinct().count());
    }

    @Test
    void rejectsTooManyChildTasksBeforeRunningAnyTask() {
        AtomicInteger calls = new AtomicInteger();
        AgentRuntime runtime = request -> {
            calls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "done", AgentState.start(request.userMessage()));
        };
        SubAgentRunner runner = new SubAgentRunner(runtime, new DelegationPolicy(1, IterationBudget.maxTurns(4)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                runner.run(new DelegationRequest(List.of(
                        new SubAgentTask("task-1", "one", "", List.of(), null),
                        new SubAgentTask("task-2", "two", "", List.of(), null)
                )))
        );

        assertEquals("too many delegated tasks: 2 > 1", error.getMessage());
        assertEquals(0, calls.get());
    }
}
