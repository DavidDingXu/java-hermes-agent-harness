package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.tool.ToolDefinition;
import com.ading.ai.hermes.tool.ToolResult;
import com.ading.ai.hermes.tool.ToolSchema;
import com.ading.ai.hermes.toolset.ToolsetCatalog;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckpointContractTest {

    @Test
    void selectsOnlyDeclaredToolsetsAndRejectsUnknownNames() {
        ToolsetCatalog catalog = ToolsetCatalog.empty().register(
                "workspace-read",
                new ToolDefinition(
                        "read_file",
                        "read a file",
                        ToolSchema.object(),
                        request -> ToolResult.success(request.callId(), "content")
                )
        );

        var selected = catalog.select(Set.of("workspace-read"));

        assertEquals(Set.of("read_file"), selected.specs().stream()
                .map(spec -> spec.name()).collect(java.util.stream.Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(Set.of("missing")));
    }
}
