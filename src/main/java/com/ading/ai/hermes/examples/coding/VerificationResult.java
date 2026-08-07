package com.ading.ai.hermes.examples.coding;

import java.util.Objects;

public record VerificationResult(String command, boolean success, String output) {

    public VerificationResult {
        Objects.requireNonNull(command, "command must not be null");
        command = command.trim();
        output = output == null ? "" : output;
        if (command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
    }
}
