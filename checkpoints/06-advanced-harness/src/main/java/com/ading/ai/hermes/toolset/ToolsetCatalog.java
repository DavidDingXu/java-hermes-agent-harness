package com.ading.ai.hermes.toolset;

import com.ading.ai.hermes.tool.ToolDefinition;
import com.ading.ai.hermes.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ToolsetCatalog {

    private final Map<String, Map<String, ToolDefinition>> definitions;
    private final Map<String, String> owners;

    private ToolsetCatalog(
            Map<String, Map<String, ToolDefinition>> definitions,
            Map<String, String> owners
    ) {
        this.definitions = Map.copyOf(definitions);
        this.owners = Map.copyOf(owners);
    }

    public static ToolsetCatalog empty() {
        return new ToolsetCatalog(Map.of(), Map.of());
    }

    public ToolsetCatalog register(String toolset, ToolDefinition definition) {
        if (toolset == null || toolset.isBlank()) {
            throw new IllegalArgumentException("toolset must not be blank");
        }
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
        nextDefinitions.put(toolset, Map.copyOf(toolsetDefinitions));

        Map<String, String> nextOwners = new LinkedHashMap<>(owners);
        nextOwners.put(definition.name(), toolset);
        return new ToolsetCatalog(nextDefinitions, nextOwners);
    }

    public ToolsetSelection select(Set<String> enabledToolsets) {
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
