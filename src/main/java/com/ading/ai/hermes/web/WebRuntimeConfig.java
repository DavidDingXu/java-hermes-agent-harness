package com.ading.ai.hermes.web;

import com.ading.ai.hermes.config.ConfigurationSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record WebRuntimeConfig(
        String baseUrl,
        String apiKey,
        String model,
        Path workspace,
        ConfigurationSource source,
        List<String> notices
) {

    public WebRuntimeConfig(String baseUrl, String apiKey, String model, Path workspace) {
        this(baseUrl, apiKey, model, workspace, ConfigurationSource.WEB_FORM, List.of());
    }

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
        source = Objects.requireNonNull(source, "source must not be null");
        notices = List.copyOf(Objects.requireNonNull(notices, "notices must not be null"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "WebRuntimeConfig[baseUrl=" + baseUrl
                + ", apiKey=[REDACTED], model=" + model
                + ", workspace=" + workspace
                + ", source=" + source
                + ", notices=" + notices + "]";
    }
}
