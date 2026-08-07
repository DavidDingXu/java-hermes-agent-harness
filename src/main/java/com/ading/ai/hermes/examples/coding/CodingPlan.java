package com.ading.ai.hermes.examples.coding;

import java.util.List;
import java.util.Objects;

public record CodingPlan(String summary, List<CodingPatch> patches, List<String> verificationCommands) {

    public CodingPlan {
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(patches, "patches must not be null");
        Objects.requireNonNull(verificationCommands, "verificationCommands must not be null");
        summary = summary.trim();
        patches = List.copyOf(patches);
        verificationCommands = verificationCommands.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(command -> !command.isBlank())
                .toList();
        if (summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (patches.isEmpty()) {
            throw new IllegalArgumentException("patches must not be empty");
        }
        if (verificationCommands.isEmpty()) {
            throw new IllegalArgumentException("verificationCommands must not be empty");
        }
    }
}
