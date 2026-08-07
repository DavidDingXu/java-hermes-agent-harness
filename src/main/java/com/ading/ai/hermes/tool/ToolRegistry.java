package com.ading.ai.hermes.tool;

import com.ading.ai.hermes.core.ToolDriver;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.model.ToolSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ToolRegistry implements ToolDriver {

    private final Map<String, ToolDefinition> definitions;

    private ToolRegistry(Map<String, ToolDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static ToolRegistry empty() {
        return new ToolRegistry(Map.of());
    }

    public ToolRegistry register(ToolDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        if (definitions.containsKey(definition.name())) {
            throw new IllegalArgumentException("tool already registered: " + definition.name());
        }
        Map<String, ToolDefinition> next = new LinkedHashMap<>(definitions);
        next.put(definition.name(), definition);
        return new ToolRegistry(next);
    }

    public List<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (ToolDefinition definition : definitions.values()) {
            specs.add(definition.spec());
        }
        return List.copyOf(specs);
    }

    @Override
    public ToolObservation execute(ToolRequest request) {
        ToolDefinition definition = definitions.get(request.name());
        if (definition == null) {
            return ToolObservation.failure(request.callId(), "tool not registered: " + request.name());
        }

        ToolArgumentValidation validation = definition.schema().validate(request.arguments());
        if (!validation.valid()) {
            return ToolObservation.failure(request.callId(), "invalid tool arguments: " + validation.message());
        }

        ToolResult result = definition.executor().execute(request);
        if (result.success()) {
            return ToolObservation.success(result.callId(), result.content());
        }
        return ToolObservation.failure(result.callId(), result.content());
    }
}
