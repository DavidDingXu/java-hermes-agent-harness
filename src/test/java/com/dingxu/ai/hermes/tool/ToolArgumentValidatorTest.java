package com.dingxu.ai.hermes.tool;

import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolArgumentValidatorTest {

    @Test
    void rejectsMissingRequiredArgumentBeforeExecutorRuns() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "read_file",
                        "Read a workspace file",
                        ToolSchema.object().requiredString("path"),
                        request -> ToolResult.success(request.callId(), "should not run")
                ));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of())
        );

        assertFalse(observation.success());
        assertEquals("call-1", observation.callId());
        assertEquals("invalid tool arguments: missing required argument: path", observation.content());
    }

    @Test
    void rejectsWrongArgumentTypeBeforeExecutorRuns() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "search",
                        "Search by query",
                        ToolSchema.object().requiredString("query"),
                        request -> ToolResult.success(request.callId(), "should not run")
                ));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "search", Map.of("query", 42))
        );

        assertFalse(observation.success());
        assertEquals("invalid tool arguments: argument query must be string", observation.content());
    }

    @Test
    void returnsBusinessValidationFailureAsToolObservation() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "read_file",
                        "Read a workspace file",
                        ToolSchema.object().requiredString("path"),
                        request -> {
                            String path = request.arguments().get("path").toString();
                            if (path.startsWith("/")) {
                                return ToolResult.failure(request.callId(), "absolute path is not allowed");
                            }
                            return ToolResult.success(request.callId(), "file:" + path);
                        }
                ));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "/etc/passwd"))
        );

        assertFalse(observation.success());
        assertEquals("absolute path is not allowed", observation.content());
    }

    @Test
    void executesToolWhenArgumentsPassSchema() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "read_file",
                        "Read a workspace file",
                        ToolSchema.object().requiredString("path"),
                        request -> ToolResult.success(request.callId(), "file:" + request.arguments().get("path"))
                ));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))
        );

        assertTrue(observation.success());
        assertEquals("file:README.md", observation.content());
    }
}
