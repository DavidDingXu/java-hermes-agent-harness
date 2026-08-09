package com.ading.ai.hermes.provider;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ProviderRuntimeResolver {

    private final Map<String, ProviderProfile> profiles;

    public ProviderRuntimeResolver(List<ProviderProfile> profiles) {
        LinkedHashMap<String, ProviderProfile> indexed = new LinkedHashMap<>();
        for (ProviderProfile profile : List.copyOf(profiles)) {
            if (indexed.putIfAbsent(profile.id(), profile) != null) {
                throw new IllegalArgumentException("provider profile already registered: " + profile.id());
            }
        }
        if (indexed.isEmpty()) {
            throw new IllegalArgumentException("at least one provider profile is required");
        }
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    public ResolvedProviderRuntime resolve(ProviderRuntimeRequest request) {
        Choice choice = chooseProvider(request);
        ProviderProfile profile = profiles.get(choice.provider());
        if (profile == null) {
            throw new IllegalArgumentException("provider profile is not registered: " + choice.provider());
        }

        String model = chooseModel(request, choice, profile);
        URI baseUrl = chooseBaseUrl(request, choice, profile);
        String apiKey = chooseApiKey(request, profile);
        return new ResolvedProviderRuntime(
                profile.id(),
                model,
                profile.apiMode(),
                baseUrl,
                apiKey,
                choice.source()
        );
    }

    private Choice chooseProvider(ProviderRuntimeRequest request) {
        if (!request.explicit().provider().isBlank()) {
            return new Choice(request.explicit().provider(), ProviderResolutionSource.EXPLICIT);
        }
        if (!request.saved().provider().isBlank()) {
            return new Choice(request.saved().provider(), ProviderResolutionSource.SAVED_CONFIG);
        }
        for (ProviderProfile profile : profiles.values()) {
            if (profile.credentialEnvironmentVariables().stream()
                    .anyMatch(name -> hasValue(request.environment().get(name)))) {
                return new Choice(profile.id(), ProviderResolutionSource.ENVIRONMENT);
            }
        }
        return new Choice(request.defaultProvider(), ProviderResolutionSource.DEFAULT);
    }

    private String chooseModel(
            ProviderRuntimeRequest request,
            Choice choice,
            ProviderProfile profile
    ) {
        if (!request.explicit().model().isBlank()) {
            return request.explicit().model();
        }
        if (choice.source() != ProviderResolutionSource.EXPLICIT
                && !request.saved().model().isBlank()) {
            return request.saved().model();
        }
        if (!profile.fallbackModels().isEmpty()) {
            return profile.fallbackModels().getFirst();
        }
        throw new IllegalArgumentException("model is required for provider: " + profile.id());
    }

    private URI chooseBaseUrl(
            ProviderRuntimeRequest request,
            Choice choice,
            ProviderProfile profile
    ) {
        if (request.explicit().baseUrl() != null) {
            return request.explicit().baseUrl();
        }
        if (choice.source() != ProviderResolutionSource.EXPLICIT
                && request.saved().baseUrl() != null) {
            return request.saved().baseUrl();
        }
        if (profile.defaultBaseUrl() != null) {
            return profile.defaultBaseUrl();
        }
        throw new IllegalArgumentException("baseUrl is required for provider: " + profile.id());
    }

    private String chooseApiKey(ProviderRuntimeRequest request, ProviderProfile profile) {
        if (!request.explicit().apiKey().isBlank()) {
            return request.explicit().apiKey();
        }
        for (String variable : profile.credentialEnvironmentVariables()) {
            String value = request.environment().get(variable);
            if (hasValue(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private record Choice(String provider, ProviderResolutionSource source) {
    }
}
