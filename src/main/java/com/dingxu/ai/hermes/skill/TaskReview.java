package com.dingxu.ai.hermes.skill;

import java.util.List;
import java.util.Objects;

public record TaskReview(
        String sessionId,
        String task,
        boolean recoveredFailure,
        List<String> successfulSteps,
        List<String> failureSignals,
        List<String> reusableLessons
) {

    public TaskReview {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(successfulSteps, "successfulSteps must not be null");
        Objects.requireNonNull(failureSignals, "failureSignals must not be null");
        Objects.requireNonNull(reusableLessons, "reusableLessons must not be null");
        sessionId = sessionId.trim();
        task = task.trim();
        successfulSteps = normalizeList(successfulSteps);
        failureSignals = normalizeList(failureSignals);
        reusableLessons = normalizeList(reusableLessons);
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (task.isBlank()) {
            throw new IllegalArgumentException("task must not be blank");
        }
    }

    public boolean hasReusableWorkflow() {
        return recoveredFailure && successfulSteps.size() >= 2 && !reusableLessons.isEmpty();
    }

    private static List<String> normalizeList(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
