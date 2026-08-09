package com.ading.ai.hermes.provider;

import java.util.Map;
import java.util.Objects;

public record ProviderRuntimeRequest(
        ProviderRuntimeOverrides explicit,
        ProviderRuntimeConfig saved,
        Map<String, String> environment,
        String defaultProvider
) {
    public ProviderRuntimeRequest {
        explicit = explicit == null ? ProviderRuntimeOverrides.empty() : explicit;
        saved = saved == null ? ProviderRuntimeConfig.empty() : saved;
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        defaultProvider = Objects.requireNonNull(defaultProvider, "defaultProvider must not be null").trim();
        if (defaultProvider.isBlank()) {
            throw new IllegalArgumentException("defaultProvider must not be blank");
        }
    }
}
