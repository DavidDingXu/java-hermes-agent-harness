package com.dingxu.ai.hermes.security;

public record ToolDecision(boolean allowed, String reason) {

    public ToolDecision {
        reason = reason == null ? "" : reason;
    }

    public static ToolDecision allow() {
        return new ToolDecision(true, "");
    }

    public static ToolDecision block(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new ToolDecision(false, reason);
    }
}
