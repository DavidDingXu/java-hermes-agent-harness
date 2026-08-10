package com.ading.ai.hermes.delegate;

import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import java.util.List;

public record SubAgentResult(
        String taskId,
        String conversationId,
        DelegationStatus status,
        String summary,
        FinishReason finishReason,
        int turnsUsed,
        IterationBudget budget,
        List<String> toolsets
) {

    public SubAgentResult {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        summary = summary == null ? "" : summary;
        toolsets = List.copyOf(toolsets);
    }

    public SubAgentResult(
            String taskId,
            DelegationStatus status,
            String summary,
            FinishReason finishReason,
            int turnsUsed,
            IterationBudget budget,
            List<String> toolsets
    ) {
        this(taskId, "subagent/" + taskId, status, summary, finishReason, turnsUsed, budget, toolsets);
    }
}
