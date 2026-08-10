package com.ading.ai.hermes.toolset;

import com.ading.ai.hermes.tool.ToolDefinition;
import com.ading.ai.hermes.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ToolsetCatalog {

    private final Map<String, Map<String, ToolDefinition>> definitions;
    private final Map<String, String> owners;

    private ToolsetCatalog(
            Map<String, Map<String, ToolDefinition>> definitions,
            Map<String, String> owners
    ) {
        Map<String, Map<String, ToolDefinition>> orderedDefinitions = new LinkedHashMap<>();
        definitions.forEach((name, tools) -> orderedDefinitions.put(
                name,
                Collections.unmodifiableMap(new LinkedHashMap<>(tools))
        ));
        this.definitions = Collections.unmodifiableMap(orderedDefinitions);
        this.owners = Collections.unmodifiableMap(new LinkedHashMap<>(owners));
    }

    public static ToolsetCatalog empty() {
        return new ToolsetCatalog(Map.of(), Map.of());
    }

    public ToolsetCatalog register(String toolset, ToolDefinition definition) {
        if (toolset == null || toolset.isBlank()) {
            throw new IllegalArgumentException("toolset must not be blank");
        }
        Objects.requireNonNull(definition, "definition must not be null");
        String owner = owners.get(definition.name());
        if (owner != null && !owner.equals(toolset)) {
            throw new IllegalArgumentException(
                    "tool '" + definition.name() + "' is already owned by toolset '" + owner + "'"
            );
        }

        Map<String, Map<String, ToolDefinition>> nextDefinitions = new LinkedHashMap<>(definitions);
        Map<String, ToolDefinition> toolsetDefinitions = new LinkedHashMap<>(
                definitions.getOrDefault(toolset, Map.of())
        );
        toolsetDefinitions.put(definition.name(), definition);
        nextDefinitions.put(
                toolset,
                Collections.unmodifiableMap(new LinkedHashMap<>(toolsetDefinitions))
        );

        Map<String, String> nextOwners = new LinkedHashMap<>(owners);
        nextOwners.put(definition.name(), toolset);
        return new ToolsetCatalog(nextDefinitions, nextOwners);
    }

    public ToolsetSelection select(Set<String> enabledToolsets) {
        Set<String> unknown = new java.util.LinkedHashSet<>(enabledToolsets);
        unknown.removeAll(definitions.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown toolsets: " + String.join(", ", unknown));
        }
        ToolRegistry registry = ToolRegistry.empty();
        Map<String, List<String>> names = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ToolDefinition>> entry : definitions.entrySet()) {
            if (!enabledToolsets.contains(entry.getKey())) {
                continue;
            }
            List<String> toolNames = new ArrayList<>();
            for (ToolDefinition definition : entry.getValue().values()) {
                registry = registry.register(definition);
                toolNames.add(definition.name());
            }
            names.put(entry.getKey(), List.copyOf(toolNames));
        }
        return new ToolsetSelection(registry, registry.specs(), names);
    }
}
