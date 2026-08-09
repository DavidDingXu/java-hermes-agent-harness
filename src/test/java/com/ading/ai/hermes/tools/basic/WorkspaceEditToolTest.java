package com.ading.ai.hermes.tools.basic;

import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.tool.ToolRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceEditToolTest {

    @TempDir
    Path workspace;

    @Test
    void replacesExactlyOneMatch() throws Exception {
        Path file = workspace.resolve("Demo.java");
        Files.writeString(file, "class Demo { int value = 1; }");
        ToolRegistry registry = new WorkspaceEditTool(workspace).registerInto(ToolRegistry.empty());

        var result = registry.execute(new ToolRequest("edit-1", "edit_file", Map.of(
                "path", "Demo.java",
                "expected", "value = 1",
                "replacement", "value = 2"
        )));

        assertTrue(result.success());
        assertEquals("class Demo { int value = 2; }", Files.readString(file));
    }

    @Test
    void refusesAmbiguousMatchWithoutChangingFile() throws Exception {
        Path file = workspace.resolve("values.txt");
        Files.writeString(file, "same\nsame\n");
        ToolRegistry registry = new WorkspaceEditTool(workspace).registerInto(ToolRegistry.empty());

        var result = registry.execute(new ToolRequest("edit-2", "edit_file", Map.of(
                "path", "values.txt",
                "expected", "same",
                "replacement", "changed"
        )));

        assertFalse(result.success());
        assertTrue(result.content().contains("2 matches"));
        assertEquals("same\nsame\n", Files.readString(file));
    }

    @Test
    void refusesToEditRuntimeStateAndLocalCredentials() throws Exception {
        Files.createDirectories(workspace.resolve(".hermes"));
        Path state = workspace.resolve(".hermes/memory.json");
        Files.writeString(state, "unchanged-state");
        Files.createDirectories(workspace.resolve("config"));
        Path credentials = workspace.resolve("config/hermes.local.properties");
        Files.writeString(credentials, "openai.api-key=reader-secret");
        ToolRegistry registry = new WorkspaceEditTool(workspace).registerInto(ToolRegistry.empty());

        var stateResult = registry.execute(new ToolRequest("edit-state", "edit_file", Map.of(
                "path", ".hermes/memory.json",
                "expected", "unchanged",
                "replacement", "changed"
        )));
        var credentialResult = registry.execute(new ToolRequest("edit-config", "edit_file", Map.of(
                "path", "config/hermes.local.properties",
                "expected", "reader-secret",
                "replacement", "changed"
        )));

        assertFalse(stateResult.success());
        assertFalse(credentialResult.success());
        assertEquals("unchanged-state", Files.readString(state));
        assertEquals("openai.api-key=reader-secret", Files.readString(credentials));
    }
}
