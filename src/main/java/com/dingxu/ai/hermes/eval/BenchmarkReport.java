package com.dingxu.ai.hermes.eval;

import java.util.List;

public record BenchmarkReport(List<BenchmarkCaseResult> results) {

    public BenchmarkReport {
        results = List.copyOf(results);
    }

    public int totalCases() {
        return results.size();
    }

    public int passedCases() {
        return (int) results.stream().filter(BenchmarkCaseResult::passed).count();
    }

    public int score() {
        return results.stream().mapToInt(BenchmarkCaseResult::score).sum();
    }

    public int maxScore() {
        return results.stream().mapToInt(BenchmarkCaseResult::maxScore).sum();
    }
}
