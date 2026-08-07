package com.dingxu.ai.hermes.delegate;

import com.dingxu.ai.hermes.core.FinishReason;
import com.dingxu.ai.hermes.core.IterationBudget;
import java.util.List;

public record SubAgentResult(
        String taskId,
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
        summary = summary == null ? "" : summary;
        toolsets = List.copyOf(toolsets);
    }
}
