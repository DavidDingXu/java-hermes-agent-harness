package com.dingxu.ai.hermes.provider;

import java.net.URI;

public record ProviderRuntimeConfig(String provider, String model, URI baseUrl) {
    public ProviderRuntimeConfig {
        provider = provider == null ? "" : provider.trim();
        model = model == null ? "" : model.trim();
    }

    public static ProviderRuntimeConfig empty() {
        return new ProviderRuntimeConfig("", "", null);
    }
}
