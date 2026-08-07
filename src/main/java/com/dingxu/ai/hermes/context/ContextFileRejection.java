package com.dingxu.ai.hermes.context;

public record ContextFileRejection(String path, String reason) {

    public ContextFileRejection {
        path = path == null ? "" : path;
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
