package com.dingxu.ai.hermes.toolset;

import com.dingxu.ai.hermes.model.ToolSpec;
import com.dingxu.ai.hermes.tool.ToolRegistry;
import java.util.List;
import java.util.Map;

public record ToolsetSelection(
        ToolRegistry registry,
        List<ToolSpec> specs,
        Map<String, List<String>> toolNamesByToolset
) {
    public ToolsetSelection {
        specs = List.copyOf(specs);
        toolNamesByToolset = Map.copyOf(toolNamesByToolset);
    }
}
