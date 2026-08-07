package com.dingxu.ai.hermes.terminal;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record TerminalCommand(
        List<String> argv,
        Path workingDirectory,
        Map<String, String> environment,
        Duration timeout,
        int maxOutputCharacters
) {
    public TerminalCommand {
        argv = List.copyOf(argv);
        environment = Map.copyOf(environment);
        if (argv.isEmpty()) {
            throw new IllegalArgumentException("argv must not be empty");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxOutputCharacters < 1) {
            throw new IllegalArgumentException("maxOutputCharacters must be positive");
        }
    }
}
