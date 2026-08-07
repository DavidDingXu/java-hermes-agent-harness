package com.dingxu.ai.hermes.toolset;

import com.dingxu.ai.hermes.tool.ToolExecutor;
import com.dingxu.ai.hermes.tool.ToolSchema;

public record McpToolDescriptor(
        String name,
        String description,
        ToolSchema schema,
        ToolExecutor executor
) {
}
