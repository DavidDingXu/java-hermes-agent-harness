package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.List;

public record ToolCallParseReport(
        List<ToolRequest> requests,
        List<ToolCallRepair> repairs,
        List<ToolCallParseError> errors
) {

    public ToolCallParseReport {
        requests = List.copyOf(requests);
        repairs = List.copyOf(repairs);
        errors = List.copyOf(errors);
    }
}
