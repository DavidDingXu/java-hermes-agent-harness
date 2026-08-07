package com.ading.ai.hermes.eval;

public record BenchmarkEvidence(boolean passed, int score, String detail) {

    public BenchmarkEvidence {
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        detail = detail == null ? "" : detail.trim();
        if (detail.isBlank()) {
            throw new IllegalArgumentException("evidence detail must not be blank");
        }
    }

    public static BenchmarkEvidence pass(int score, String detail) {
        return new BenchmarkEvidence(true, score, detail);
    }

    public static BenchmarkEvidence fail(String detail) {
        return new BenchmarkEvidence(false, 0, detail);
    }
}
