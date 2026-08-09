package com.ading.ai.hermes.verification;

public record CompletionDecision(boolean eligible, boolean accepted, String detail) {

    public CompletionDecision {
        detail = detail == null ? "" : detail;
    }

    public static CompletionDecision notEligible(String detail) {
        return new CompletionDecision(false, false, detail);
    }

    public static CompletionDecision from(CompletionEvidence evidence) {
        return new CompletionDecision(true, evidence.accepted(), evidence.detail());
    }
}
