package com.ading.ai.hermes.tool;

import com.ading.ai.hermes.model.ToolSpec;
import java.util.Map;
import java.util.Objects;

public record ToolDefinition(String name, String description, ToolSchema schema, ToolExecutor executor) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        description = description == null ? "" : description;
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
    }

    public ToolDefinition(String name, String description, Map<String, String> parameters, ToolExecutor executor) {
        this(name, description, schemaFrom(parameters), executor);
    }

    public ToolSpec spec() {
        return new ToolSpec(name, description, schema.parameters());
    }

    private static ToolSchema schemaFrom(Map<String, String> parameters) {
        ToolSchema schema = ToolSchema.object();
        for (Map.Entry<String, String> entry : Map.copyOf(parameters).entrySet()) {
            if ("string".equals(entry.getValue())) {
                schema = schema.requiredString(entry.getKey());
            }
        }
        return schema;
    }
}
