package com.ading.ai.hermes.context;

public record ContextFile(String path, String content) {

    public ContextFile {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        content = content == null ? "" : content;
    }
}
