package com.ading.ai.hermes.terminal;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TerminalCommand(
        List<String> argv,
        Path workingDirectory,
        Map<String, String> environment,
        Duration timeout,
        int maxOutputCharacters
) {
    public TerminalCommand {
        argv = List.copyOf(Objects.requireNonNull(argv, "argv must not be null"));
        workingDirectory = Objects.requireNonNull(
                workingDirectory,
                "workingDirectory must not be null"
        );
        environment = Map.copyOf(Objects.requireNonNull(
                environment,
                "environment must not be null"
        ));
        if (argv.isEmpty() || argv.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("argv must contain only non-blank values");
        }
        if (environment.keySet().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("environment names must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxOutputCharacters < 1) {
            throw new IllegalArgumentException("maxOutputCharacters must be positive");
        }
    }
}
