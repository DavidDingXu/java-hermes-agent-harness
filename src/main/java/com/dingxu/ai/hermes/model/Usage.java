package com.dingxu.ai.hermes.model;

public record Usage(int inputTokens, int outputTokens) {

    public Usage {
        if (inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens must not be negative");
        }
        if (outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens must not be negative");
        }
    }

    public static Usage empty() {
        return new Usage(0, 0);
    }
}
