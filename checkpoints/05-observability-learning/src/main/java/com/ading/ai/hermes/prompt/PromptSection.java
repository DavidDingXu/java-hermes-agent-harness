package com.ading.ai.hermes.prompt;

import java.util.Objects;

public record PromptSection(PromptTier tier, String name, String content) {

    public PromptSection {
        Objects.requireNonNull(tier, "tier must not be null");
        name = requireText(name, "name");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
