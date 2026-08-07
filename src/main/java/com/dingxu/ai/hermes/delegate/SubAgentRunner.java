package com.dingxu.ai.hermes.delegate;

import com.dingxu.ai.hermes.core.AgentRunRequest;
import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentRuntime;
import com.dingxu.ai.hermes.core.FinishReason;
import com.dingxu.ai.hermes.core.IterationBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SubAgentRunner {

    private final AgentRuntime runtime;
    private final DelegationPolicy policy;

    public SubAgentRunner(AgentRuntime runtime, DelegationPolicy policy) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public DelegationResult run(DelegationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.tasks().size() > policy.maxConcurrentChildren()) {
            throw new IllegalArgumentException(
                    "too many delegated tasks: " + request.tasks().size() + " > " + policy.maxConcurrentChildren()
            );
        }

        List<SubAgentResult> results = new ArrayList<>();
        for (SubAgentTask task : request.tasks()) {
            IterationBudget budget = task.budget() == null ? policy.defaultBudget() : task.budget();
            AgentRunResult runResult = runtime.run(AgentRunRequest.start(childUserMessage(task), budget));
            results.add(new SubAgentResult(
                    task.id(),
                    statusOf(runResult.finishReason()),
                    runResult.finalAnswer(),
                    runResult.finishReason(),
                    runResult.state().turnsUsed(),
                    budget,
                    task.toolsets()
            ));
        }
        return new DelegationResult(results);
    }

    private DelegationStatus statusOf(FinishReason finishReason) {
        if (finishReason == FinishReason.FINAL_ANSWER) {
            return DelegationStatus.COMPLETED;
        }
        return DelegationStatus.FAILED;
    }

    private String childUserMessage(SubAgentTask task) {
        if (task.context().isBlank()) {
            return task.goal();
        }
        return task.goal() + "\n\nContext:\n" + task.context();
    }
}
