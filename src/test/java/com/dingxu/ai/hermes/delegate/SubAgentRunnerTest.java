package com.dingxu.ai.hermes.delegate;

import com.dingxu.ai.hermes.core.AgentRunRequest;
import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentRuntime;
import com.dingxu.ai.hermes.core.AgentState;
import com.dingxu.ai.hermes.core.FinishReason;
import com.dingxu.ai.hermes.core.IterationBudget;
import com.dingxu.ai.hermes.core.AgentEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
                        AgentEvent.toolRequested(new com.dingxu.ai.hermes.core.ToolRequest("call-1", "read_file", Map.of("path", "secret.log"))),
                        AgentEvent.toolObserved(com.dingxu.ai.hermes.core.ToolObservation.success("call-1", "large internal output")),
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
