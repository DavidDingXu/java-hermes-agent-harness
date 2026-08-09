package com.ading.ai.hermes.security;

import com.ading.ai.hermes.core.ToolRequest;

@FunctionalInterface
public interface ToolPolicy {

    ToolDecision decide(ToolRequest request);

    static ToolPolicy allowAll() {
        return request -> ToolDecision.allow();
    }

    static ToolPolicy blockTool(String toolName, String reason) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        return request -> {
            if (request.name().equals(toolName)) {
                return ToolDecision.block(reason);
            }
            return ToolDecision.allow();
        };
    }

    static ToolPolicy blockArgumentContaining(String argumentName, String fragment, String reason) {
        if (argumentName == null || argumentName.isBlank()) {
            throw new IllegalArgumentException("argumentName must not be blank");
        }
        if (fragment == null || fragment.isBlank()) {
            throw new IllegalArgumentException("fragment must not be blank");
        }
        return request -> {
            Object value = request.arguments().get(argumentName);
            if (value instanceof String text && text.contains(fragment)) {
                return ToolDecision.block(reason);
            }
            return ToolDecision.allow();
        };
    }

    static ToolPolicy workspaceRelativePath(String argumentName) {
        if (argumentName == null || argumentName.isBlank()) {
            throw new IllegalArgumentException("argumentName must not be blank");
        }
        return request -> {
            Object value = request.arguments().get(argumentName);
            if (!(value instanceof String path)) {
                return ToolDecision.allow();
            }
            String normalized = path.trim().replace('\\', '/');
            boolean absolute = normalized.startsWith("/")
                    || normalized.startsWith("//")
                    || normalized.matches("^[A-Za-z]:/.*");
            boolean traversal = java.util.Arrays.stream(normalized.split("/"))
                    .anyMatch(".."::equals);
            if (absolute || traversal || normalized.indexOf('\0') >= 0) {
                return ToolDecision.block("path must stay inside the workspace");
            }
            return ToolDecision.allow();
        };
    }
}
