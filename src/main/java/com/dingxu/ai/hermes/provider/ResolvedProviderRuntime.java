package com.dingxu.ai.hermes.provider;

import java.net.URI;
import java.util.Objects;

public record ResolvedProviderRuntime(
        String provider,
        String model,
        ProviderApiMode apiMode,
        URI baseUrl,
        String apiKey,
        ProviderResolutionSource source
) {
    public ResolvedProviderRuntime {
        Objects.requireNonNull(apiMode, "apiMode must not be null");
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        apiKey = apiKey == null ? "" : apiKey;
        Objects.requireNonNull(source, "source must not be null");
    }
}
