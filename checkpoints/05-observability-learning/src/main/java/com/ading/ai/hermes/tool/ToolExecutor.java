package com.ading.ai.hermes.tool;

import com.ading.ai.hermes.core.ToolRequest;

@FunctionalInterface
public interface ToolExecutor {

    ToolResult execute(ToolRequest request);
}
