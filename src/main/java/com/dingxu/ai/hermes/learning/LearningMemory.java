package com.dingxu.ai.hermes.learning;

public record LearningMemory(String id, String content) {

    public LearningMemory {
        id = requireText(id, "memory id");
        content = requireText(content, "memory content");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
