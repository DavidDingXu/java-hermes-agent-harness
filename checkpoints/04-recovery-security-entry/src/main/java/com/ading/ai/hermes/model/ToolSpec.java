package com.ading.ai.hermes.model;

import java.util.Map;

public record ToolSpec(String name, String description, Map<String, String> parameters) {

    public ToolSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        description = description == null ? "" : description;
        parameters = Map.copyOf(parameters);
    }
}
