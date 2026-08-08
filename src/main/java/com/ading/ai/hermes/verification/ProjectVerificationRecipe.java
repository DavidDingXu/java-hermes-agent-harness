package com.ading.ai.hermes.verification;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ProjectVerificationRecipe(
        String name,
        Path projectRoot,
        List<ProjectVerificationCommand> commands,
        List<String> evidence
) {

    public ProjectVerificationRecipe {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        projectRoot = Objects.requireNonNull(
                projectRoot,
                "projectRoot must not be null"
        ).toAbsolutePath().normalize();
        commands = List.copyOf(Objects.requireNonNull(
                commands,
                "commands must not be null"
        ));
        evidence = List.copyOf(Objects.requireNonNull(
                evidence,
                "evidence must not be null"
        ));
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("verification recipe must contain a command");
        }
        if (evidence.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("evidence must contain only non-blank values");
        }
        name = name.trim();
    }
}
