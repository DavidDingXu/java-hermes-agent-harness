package com.ading.ai.hermes.toolset;

import com.ading.ai.hermes.tool.ToolExecutor;
import com.ading.ai.hermes.tool.ToolSchema;

public record McpToolDescriptor(
        String name,
        String description,
        ToolSchema schema,
        ToolExecutor executor
) {
}
