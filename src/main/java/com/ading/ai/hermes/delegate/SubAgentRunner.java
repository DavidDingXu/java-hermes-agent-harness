package com.ading.ai.hermes.delegate;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.CancellableAgentRuntime;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.StopSignal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SubAgentRunner {

    private final CancellableAgentRuntime runtime;
    private final DelegationPolicy policy;

    public SubAgentRunner(AgentRuntime runtime, DelegationPolicy policy) {
        this.runtime = CancellableAgentRuntime.adapt(runtime);
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public DelegationResult run(DelegationRequest request) {
        return run(request, StopSignal.none());
    }

    public DelegationResult run(DelegationRequest request, StopSignal parentStopSignal) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(parentStopSignal, "parentStopSignal must not be null");
        if (request.tasks().size() > policy.maxConcurrentChildren()) {
            throw new IllegalArgumentException(
                    "too many delegated tasks: " + request.tasks().size() + " > " + policy.maxConcurrentChildren()
            );
        }

        List<SubAgentResult> results = new ArrayList<>();
        for (SubAgentTask task : request.tasks()) {
            IterationBudget budget = task.budget() == null ? policy.defaultBudget() : task.budget();
            String conversationId = childConversationId(request, task);
            if (parentStopSignal.stopRequested()) {
                results.add(new SubAgentResult(
                        task.id(),
                        conversationId,
                        DelegationStatus.INTERRUPTED,
                        parentStopSignal.reason(),
                        FinishReason.INTERRUPTED,
                        0,
                        budget,
                        task.toolsets()
                ));
                continue;
            }
            AgentRunResult runResult = runtime.run(AgentRunRequest.from(
                    "subagent",
                    conversationId,
                    childUserMessage(task),
                    budget,
                    childMetadata(request, task)
            ), parentStopSignal);
            results.add(new SubAgentResult(
                    task.id(),
                    conversationId,
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
        if (finishReason == FinishReason.INTERRUPTED) {
            return DelegationStatus.INTERRUPTED;
        }
        return DelegationStatus.FAILED;
    }

    private Map<String, String> childMetadata(DelegationRequest request, SubAgentTask task) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(SubAgentMetadata.DELEGATION_ID, request.delegationId());
        metadata.put(SubAgentMetadata.TASK_ID, task.id());
        metadata.put(SubAgentMetadata.TOOLSETS, String.join(",", task.toolsets()));
        if (!request.parentRunId().isBlank()) {
            metadata.put(SubAgentMetadata.PARENT_RUN_ID, request.parentRunId());
        }
        if (!request.parentConversationId().isBlank()) {
            metadata.put(SubAgentMetadata.PARENT_CONVERSATION_ID, request.parentConversationId());
        }
        return Map.copyOf(metadata);
    }

    private String childConversationId(DelegationRequest request, SubAgentTask task) {
        String prefix = request.parentConversationId().isBlank()
                ? "subagent"
                : safeIdPart(request.parentConversationId()) + ".subagent";
        return prefix + "." + safeIdPart(request.delegationId()) + "." + safeIdPart(task.id());
    }

    private String safeIdPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String childUserMessage(SubAgentTask task) {
        if (task.context().isBlank()) {
            return task.goal();
        }
        return task.goal() + "\n\nContext:\n" + task.context();
    }
}
