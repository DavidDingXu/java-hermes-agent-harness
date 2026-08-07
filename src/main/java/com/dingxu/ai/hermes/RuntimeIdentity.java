package com.dingxu.ai.hermes;

public record RuntimeIdentity(String projectName, String runtimeBoundary) {

    public static RuntimeIdentity initial() {
        return new RuntimeIdentity(
                "java-hermes-agent-harness",
                "Hermes-style Agent Runtime in Java"
        );
    }
}
