package com.dingxu.ai.hermes.provider;

import java.net.URI;

public record ProviderRuntimeOverrides(
        String provider,
        String model,
        URI baseUrl,
        String apiKey
) {
    public ProviderRuntimeOverrides {
        provider = clean(provider);
        model = clean(model);
        apiKey = clean(apiKey);
    }

    public static ProviderRuntimeOverrides empty() {
        return new ProviderRuntimeOverrides("", "", null, "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
