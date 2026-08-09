package com.ading.ai.hermes.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolSchema {

    private final Map<String, ToolArgumentSpec> arguments;

    private ToolSchema(Map<String, ToolArgumentSpec> arguments) {
        this.arguments = Map.copyOf(arguments);
    }

    public static ToolSchema object() {
        return new ToolSchema(Map.of());
    }

    public ToolSchema requiredString(String name) {
        Map<String, ToolArgumentSpec> next = new LinkedHashMap<>(arguments);
        next.put(name, new ToolArgumentSpec(name, ToolArgumentType.STRING, true));
        return new ToolSchema(next);
    }

    public Map<String, String> parameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        for (ToolArgumentSpec spec : arguments.values()) {
            parameters.put(spec.name(), spec.type().name().toLowerCase());
        }
        return Map.copyOf(parameters);
    }

    public ToolArgumentValidation validate(Map<String, Object> values) {
        for (ToolArgumentSpec spec : arguments.values()) {
            Object value = values.get(spec.name());
            if (value == null && spec.required()) {
                return ToolArgumentValidation.invalid("missing required argument: " + spec.name());
            }
            if (value != null && spec.type() == ToolArgumentType.STRING && !(value instanceof String)) {
                return ToolArgumentValidation.invalid("argument " + spec.name() + " must be string");
            }
        }
        return ToolArgumentValidation.ok();
    }
}
