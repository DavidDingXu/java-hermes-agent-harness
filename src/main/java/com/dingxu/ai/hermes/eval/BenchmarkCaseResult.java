package com.dingxu.ai.hermes.eval;

public record BenchmarkCaseResult(
        String id,
        boolean passed,
        int score,
        int maxScore,
        String evidence
) {

    public BenchmarkCaseResult {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("case result id must not be blank");
        }
        if (score < 0 || maxScore < 1 || score > maxScore) {
            throw new IllegalArgumentException("case score must be between 0 and maxScore");
        }
        evidence = evidence == null ? "" : evidence;
    }
}
