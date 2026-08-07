package com.dingxu.ai.hermes.terminal;

public record TerminalResult(
        TerminalStatus status,
        String output,
        int exitCode,
        boolean truncated
) {
}
