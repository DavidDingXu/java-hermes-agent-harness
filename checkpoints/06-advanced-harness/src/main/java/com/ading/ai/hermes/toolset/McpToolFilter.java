package com.ading.ai.hermes.toolset;

import java.util.Set;
import java.util.regex.Pattern;

public record McpToolFilter(Set<String> include, Set<String> exclude) {

    public McpToolFilter {
        include = Set.copyOf(include);
        exclude = Set.copyOf(exclude);
    }

    public static McpToolFilter all() {
        return new McpToolFilter(Set.of(), Set.of());
    }

    public boolean allows(String toolName) {
        if (!include.isEmpty()) {
            return include.stream().anyMatch(pattern -> matches(toolName, pattern));
        }
        return exclude.stream().noneMatch(pattern -> matches(toolName, pattern));
    }

    private static boolean matches(String value, String glob) {
        String regex = "^" + Pattern.quote(glob).replace("*", "\\E.*\\Q") + "$";
        return value.matches(regex);
    }
}
