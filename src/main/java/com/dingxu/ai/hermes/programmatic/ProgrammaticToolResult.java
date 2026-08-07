package com.dingxu.ai.hermes.programmatic;

public record ProgrammaticToolResult(
        ProgrammaticToolStatus status,
        String output,
        int toolCalls
) {
}
