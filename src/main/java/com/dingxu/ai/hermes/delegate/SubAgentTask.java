package com.dingxu.ai.hermes.delegate;

import com.dingxu.ai.hermes.core.IterationBudget;
import java.util.List;
import java.util.Objects;

public record SubAgentTask(
        String id,
        String goal,
        String context,
        List<String> toolsets,
        IterationBudget budget
) {

    public SubAgentTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal must not be blank");
        }
        context = context == null ? "" : context;
        toolsets = List.copyOf(Objects.requireNonNullElse(toolsets, List.of()));
    }
}
