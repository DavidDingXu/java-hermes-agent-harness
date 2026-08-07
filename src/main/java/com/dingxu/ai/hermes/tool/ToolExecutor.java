package com.dingxu.ai.hermes.tool;

import com.dingxu.ai.hermes.core.ToolRequest;

@FunctionalInterface
public interface ToolExecutor {

    ToolResult execute(ToolRequest request);
}
