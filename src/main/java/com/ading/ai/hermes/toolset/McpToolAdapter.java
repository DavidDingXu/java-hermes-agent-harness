package com.ading.ai.hermes.toolset;

import com.ading.ai.hermes.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class McpToolAdapter {

    private final String serverName;
    private final McpToolSource source;
    private final McpToolFilter filter;

    public McpToolAdapter(String serverName, McpToolSource source, McpToolFilter filter) {
        if (serverName == null || serverName.isBlank()) {
            throw new IllegalArgumentException("serverName must not be blank");
        }
        this.serverName = normalizePart(serverName);
        this.source = source;
        this.filter = filter;
    }

    public ToolsetCatalog registerInto(ToolsetCatalog catalog) {
        List<McpToolDescriptor> discovered;
        try {
            discovered = source.discover();
        } catch (Exception exception) {
            throw new McpRegistrationException("MCP discovery failed for " + serverName, exception);
        }

        Map<String, List<McpToolDescriptor>> byNormalizedName = new LinkedHashMap<>();
        for (McpToolDescriptor descriptor : discovered) {
            if (!filter.allows(descriptor.name())) {
                continue;
            }
            String name = registryName(descriptor.name());
            byNormalizedName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(descriptor);
        }

        List<String> collisions = byNormalizedName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (!collisions.isEmpty()) {
            throw new McpRegistrationException(
                    "MCP normalization collision: " + String.join(", ", collisions)
            );
        }

        ToolsetCatalog next = catalog;
        String toolset = "mcp-" + serverName;
        for (Map.Entry<String, List<McpToolDescriptor>> entry : byNormalizedName.entrySet()) {
            McpToolDescriptor descriptor = entry.getValue().getFirst();
            try {
                next = next.register(toolset, new ToolDefinition(
                        entry.getKey(), descriptor.description(), descriptor.schema(), descriptor.executor()
                ));
            } catch (IllegalArgumentException exception) {
                throw new McpRegistrationException(
                        "MCP tool collision for '" + entry.getKey() + "': " + exception.getMessage(),
                        exception
                );
            }
        }
        return next;
    }

    private String registryName(String toolName) {
        return "mcp_" + serverName + "_" + normalizePart(toolName);
    }

    private static String normalizePart(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return normalized.replaceAll("^_+|_+$", "");
    }
}
