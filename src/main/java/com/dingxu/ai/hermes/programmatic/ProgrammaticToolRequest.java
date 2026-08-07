package com.dingxu.ai.hermes.programmatic;

import java.time.Duration;
import java.util.Set;

public record ProgrammaticToolRequest(
        String programName,
        ToolProgram program,
        Set<String> allowedTools,
        int maxToolCalls,
        Duration timeout
) {
    public ProgrammaticToolRequest {
        if (programName == null || programName.isBlank()) {
            throw new IllegalArgumentException("programName must not be blank");
        }
        if (program == null) {
            throw new IllegalArgumentException("program must not be null");
        }
        allowedTools = Set.copyOf(allowedTools);
        if (maxToolCalls < 1) {
            throw new IllegalArgumentException("maxToolCalls must be positive");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
