package com.ading.ai.hermes.verification;

import java.util.List;
import java.util.Objects;

public record ProjectVerificationCommand(String phase, List<String> argv) {

    public ProjectVerificationCommand {
        if (phase == null || phase.isBlank()) {
            throw new IllegalArgumentException("phase must not be blank");
        }
        argv = List.copyOf(Objects.requireNonNull(argv, "argv must not be null"));
        if (argv.isEmpty() || argv.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("argv must contain only non-blank values");
        }
        phase = phase.trim();
    }
}
