package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.tool.ToolRegistry;
import com.ading.ai.hermes.tools.basic.WorkspaceEditTool;
import com.ading.ai.hermes.tools.basic.WorkspaceFileTools;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointContractTest {

    @TempDir
    Path workspace;

    @Test
    void editsOneWorkspaceMatchAndRejectsPathEscape() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "状态：待验证");
        ToolRegistry tools = new WorkspaceFileTools(workspace, 8_192)
                .registerInto(ToolRegistry.empty());
        tools = new WorkspaceEditTool(workspace).registerInto(tools);

        var edit = tools.execute(new ToolRequest("edit-1", "edit_file", Map.of(
                "path", "README.md",
                "expected", "待验证",
                "replacement", "已验证"
        )));
        var escape = tools.execute(new ToolRequest(
                "read-1", "read_file", Map.of("path", "../secret.txt")
        ));

        assertTrue(edit.success());
        assertTrue(Files.readString(workspace.resolve("README.md")).contains("已验证"));
        assertFalse(escape.success());
    }
}
