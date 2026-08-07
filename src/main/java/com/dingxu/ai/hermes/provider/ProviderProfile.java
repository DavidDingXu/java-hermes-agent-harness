package com.dingxu.ai.hermes.provider;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record ProviderProfile(
        String id,
        ProviderApiMode apiMode,
        URI defaultBaseUrl,
        List<String> credentialEnvironmentVariables,
        List<String> fallbackModels
) {
    public ProviderProfile {
        id = id == null ? "" : id.trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("provider id must not be blank");
        }
        Objects.requireNonNull(apiMode, "apiMode must not be null");
        credentialEnvironmentVariables = List.copyOf(credentialEnvironmentVariables);
        fallbackModels = List.copyOf(fallbackModels);
    }
}
