package com.ading.ai.hermes.toolset;

import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.tool.ToolDefinition;
import com.ading.ai.hermes.tool.ToolResult;
import com.ading.ai.hermes.tool.ToolSchema;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsetCatalogTest {

    @Test
    void selectsOnlyToolsFromEnabledToolsets() {
        ToolsetCatalog catalog = ToolsetCatalog.empty()
                .register("workspace", tool("read_file", "read"))
                .register("terminal", tool("terminal", "shell"));

        ToolsetSelection selection = catalog.select(Set.of("workspace"));

        assertEquals(List.of("read_file"), selection.specs().stream().map(spec -> spec.name()).toList());
        assertTrue(selection.registry().execute(new ToolRequest("1", "read_file", java.util.Map.of())).success());
        assertFalse(selection.registry().execute(new ToolRequest("2", "terminal", java.util.Map.of())).success());
    }

    @Test
    void preservesRegistrationOrderForStablePromptsAndToolDisplays() {
        ToolsetCatalog catalog = ToolsetCatalog.empty()
                .register("workspace", tool("read_file", "read"))
                .register("workspace", tool("list_directory", "list"))
                .register("workspace", tool("edit_file", "edit"));

        ToolsetSelection selection = catalog.select(Set.of("workspace"));

        assertEquals(
                List.of("read_file", "list_directory", "edit_file"),
                selection.specs().stream().map(spec -> spec.name()).toList()
        );
    }

    @Test
    void registersFilteredMcpToolsAsOneDynamicToolset() {
        McpToolSource source = () -> List.of(
                descriptor("search", "search-result"),
                descriptor("delete_all", "deleted")
        );
        McpToolAdapter adapter = new McpToolAdapter(
                "docs", source, new McpToolFilter(Set.of("search"), Set.of())
        );

        ToolsetCatalog catalog = adapter.registerInto(ToolsetCatalog.empty());
        ToolsetSelection selection = catalog.select(Set.of("mcp-docs"));
        ToolObservation result = selection.registry().execute(
                new ToolRequest("1", "mcp_docs_search", java.util.Map.of())
        );

        assertTrue(result.success());
        assertEquals("search-result", result.content());
        assertEquals(List.of("mcp_docs_search"), selection.toolNamesByToolset().get("mcp-docs"));
    }

    @Test
    void rejectsEveryMcpToolThatCollidesAfterNameNormalization() {
        McpToolSource source = () -> List.of(
                descriptor("read-file", "first"),
                descriptor("read_file", "second")
        );

        McpRegistrationException error = org.junit.jupiter.api.Assertions.assertThrows(
                McpRegistrationException.class,
                () -> new McpToolAdapter("repo", source, McpToolFilter.all())
                        .registerInto(ToolsetCatalog.empty())
        );

        assertTrue(error.getMessage().contains("normalization collision"));
    }

    private static ToolDefinition tool(String name, String content) {
        return new ToolDefinition(
                name, name, ToolSchema.object(),
                request -> ToolResult.success(request.callId(), content)
        );
    }

    private static McpToolDescriptor descriptor(String name, String content) {
        return new McpToolDescriptor(
                name, name, ToolSchema.object(),
                request -> ToolResult.success(request.callId(), content)
        );
    }
}
