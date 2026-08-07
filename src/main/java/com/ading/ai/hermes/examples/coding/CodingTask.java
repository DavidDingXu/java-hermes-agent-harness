package com.ading.ai.hermes.examples.coding;

import java.util.List;
import java.util.Objects;

public record CodingTask(String goal, List<String> contextPaths) {

    public CodingTask {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(contextPaths, "contextPaths must not be null");
        goal = goal.trim();
        contextPaths = List.copyOf(contextPaths);
        if (goal.isBlank()) {
            throw new IllegalArgumentException("goal must not be blank");
        }
    }
}
