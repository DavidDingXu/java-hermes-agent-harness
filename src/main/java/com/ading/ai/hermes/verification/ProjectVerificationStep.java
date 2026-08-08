package com.ading.ai.hermes.verification;

import com.ading.ai.hermes.terminal.TerminalStatus;
import java.util.Objects;

public record ProjectVerificationStep(
        String phase,
        TerminalStatus status,
        int exitCode,
        String output,
        boolean truncated
) {
    public ProjectVerificationStep {
        if (phase == null || phase.isBlank()) {
            throw new IllegalArgumentException("phase must not be blank");
        }
        phase = phase.trim();
        status = Objects.requireNonNull(status, "status must not be null");
        output = output == null ? "" : output;
    }
}
