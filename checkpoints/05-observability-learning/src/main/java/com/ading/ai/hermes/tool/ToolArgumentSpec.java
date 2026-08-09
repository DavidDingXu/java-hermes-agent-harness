package com.ading.ai.hermes.tool;

import java.util.Objects;

public record ToolArgumentSpec(String name, ToolArgumentType type, boolean required) {

    public ToolArgumentSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
    }
}
