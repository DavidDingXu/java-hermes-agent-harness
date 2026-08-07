package com.dingxu.ai.hermes.eval;

import java.util.Objects;

public record BenchmarkCase(
        String id,
        String prompt,
        int maxTurns,
        int maxScore,
        BenchmarkEvaluator evaluator
) {

    public BenchmarkCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("case id must not be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("case prompt must not be blank");
        }
        if (maxTurns < 1) {
            throw new IllegalArgumentException("maxTurns must be at least 1");
        }
        if (maxScore < 1) {
            throw new IllegalArgumentException("maxScore must be at least 1");
        }
        Objects.requireNonNull(evaluator, "evaluator must not be null");
        id = id.trim();
        prompt = prompt.trim();
    }
}
