package com.dingxu.ai.hermes.verification;

public record CompletionEvidence(boolean accepted, String detail) {

    public CompletionEvidence {
        detail = detail == null ? "" : detail.trim();
        if (detail.isBlank()) {
            throw new IllegalArgumentException("evidence detail must not be blank");
        }
    }

    public static CompletionEvidence accept(String detail) {
        return new CompletionEvidence(true, detail);
    }

    public static CompletionEvidence reject(String detail) {
        return new CompletionEvidence(false, detail);
    }
}
