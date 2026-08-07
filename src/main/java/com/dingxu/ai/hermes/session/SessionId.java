package com.dingxu.ai.hermes.session;

public record SessionId(String value) {

    private static final String SAFE_PATTERN = "[A-Za-z0-9._-]+";

    public SessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (!value.matches(SAFE_PATTERN)) {
            throw new IllegalArgumentException("sessionId must contain only letters, numbers, dot, dash or underscore");
        }
    }
}
