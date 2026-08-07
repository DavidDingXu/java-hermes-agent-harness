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
}
