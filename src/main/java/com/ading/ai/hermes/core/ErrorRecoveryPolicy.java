package com.ading.ai.hermes.core;

public record ErrorRecoveryPolicy(int maxRecoveries) {

    public ErrorRecoveryPolicy {
        if (maxRecoveries < 0) {
            throw new IllegalArgumentException("maxRecoveries must not be negative");
        }
    }

    public static ErrorRecoveryPolicy maxRecoveries(int maxRecoveries) {
        return new ErrorRecoveryPolicy(maxRecoveries);
    }
}
