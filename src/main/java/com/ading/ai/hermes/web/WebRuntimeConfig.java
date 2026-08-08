package com.ading.ai.hermes.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record WebRuntimeConfig(String baseUrl, String apiKey, String model, Path workspace) {

    public WebRuntimeConfig {
        baseUrl = requireText(baseUrl, "baseUrl");
        apiKey = requireText(apiKey, "apiKey");
        model = requireText(model, "model");
        workspace = Objects.requireNonNull(workspace, "workspace must not be null")
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("workspace must be an existing directory");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
