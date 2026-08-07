package com.dingxu.ai.hermes.tool;

import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void dispatchesRegisteredToolByName() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "echo",
                        "Return text as-is",
                        Map.of("text", "string"),
                        request -> ToolResult.success(request.callId(), request.arguments().get("text").toString())
                ))
                .register(new ToolDefinition(
                        "read_file",
                        "Read a workspace file",
                        Map.of("path", "string"),
                        request -> ToolResult.success(request.callId(), "file:" + request.arguments().get("path"))
                ));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))
        );

        assertTrue(observation.success());
        assertEquals("call-1", observation.callId());
        assertEquals("file:README.md", observation.content());
    }

    @Test
    void returnsFailureObservationForUnknownTool() {
        ToolRegistry registry = ToolRegistry.empty();

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "missing_tool", Map.of())
        );

        assertEquals("call-1", observation.callId());
        assertEquals(false, observation.success());
        assertEquals("tool not registered: missing_tool", observation.content());
    }

    @Test
    void exposesToolSpecsWithoutExecutors() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "echo",
                        "Return text as-is",
                        Map.of("text", "string"),
                        request -> ToolResult.success(request.callId(), "ok")
                ));

        assertEquals(1, registry.specs().size());
        assertEquals("echo", registry.specs().get(0).name());
        assertEquals("Return text as-is", registry.specs().get(0).description());
        assertEquals(Map.of("text", "string"), registry.specs().get(0).parameters());
    }

    @Test
    void rejectsDuplicateToolNamesInsteadOfSilentlyReplacingBehavior() {
        ToolDefinition first = new ToolDefinition(
                "echo",
                "first",
                Map.of(),
                request -> ToolResult.success(request.callId(), "first")
        );
        ToolDefinition replacement = new ToolDefinition(
                "echo",
                "replacement",
                Map.of(),
                request -> ToolResult.success(request.callId(), "replacement")
        );
        ToolRegistry registry = ToolRegistry.empty().register(first);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(replacement)
        );

        assertTrue(error.getMessage().contains("already registered"));
    }
}
