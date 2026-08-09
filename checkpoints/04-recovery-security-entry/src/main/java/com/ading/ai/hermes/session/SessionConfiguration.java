package com.ading.ai.hermes.session;

import java.nio.file.Path;
import java.util.Objects;

public record SessionConfiguration(Path workingDirectory, String model) {

    public SessionConfiguration {
        workingDirectory = Objects.requireNonNull(
                workingDirectory,
                "workingDirectory must not be null"
        ).toAbsolutePath().normalize();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        model = model.trim();
    }
}
