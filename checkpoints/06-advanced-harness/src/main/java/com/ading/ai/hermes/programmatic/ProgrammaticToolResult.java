package com.ading.ai.hermes.programmatic;

public record ProgrammaticToolResult(
        ProgrammaticToolStatus status,
        String output,
        int toolCalls
) {
}
