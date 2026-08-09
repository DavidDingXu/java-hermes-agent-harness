package com.ading.ai.hermes.provider;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRuntimeResolverTest {

    private final ProviderRuntimeResolver resolver = new ProviderRuntimeResolver(List.of(
            new ProviderProfile(
                    "openrouter",
                    ProviderApiMode.CHAT_COMPLETIONS,
                    URI.create("https://openrouter.ai/api/v1"),
                    List.of("OPENROUTER_API_KEY"),
                    List.of("openrouter/auto")
            ),
            new ProviderProfile(
                    "custom",
                    ProviderApiMode.CHAT_COMPLETIONS,
                    null,
                    List.of("OPENAI_API_KEY"),
                    List.of()
            ),
            new ProviderProfile(
                    "anthropic",
                    ProviderApiMode.ANTHROPIC_MESSAGES,
                    URI.create("https://api.anthropic.com"),
                    List.of("ANTHROPIC_API_KEY"),
                    List.of("claude-sonnet")
            )
    ));

    @Test
    void explicitRuntimeChoiceWinsOverSavedConfigAndEnvironmentDetection() {
        ResolvedProviderRuntime runtime = resolver.resolve(new ProviderRuntimeRequest(
                new ProviderRuntimeOverrides(
                        "custom",
                        "local-model",
                        URI.create("http://127.0.0.1:11434/v1"),
                        "explicit-key"
                ),
                new ProviderRuntimeConfig("anthropic", "saved-model", null),
                Map.of(
                        "ANTHROPIC_API_KEY", "anthropic-key",
                        "OPENROUTER_API_KEY", "openrouter-key",
                        "OPENAI_API_KEY", "openai-key"
                ),
                "openrouter"
        ));

        assertEquals("custom", runtime.provider());
        assertEquals("local-model", runtime.model());
        assertEquals("explicit-key", runtime.apiKey());
        assertEquals(ProviderResolutionSource.EXPLICIT, runtime.source());
    }

    @Test
    void scopesEnvironmentCredentialToTheSelectedProvider() {
        ResolvedProviderRuntime runtime = resolver.resolve(new ProviderRuntimeRequest(
                ProviderRuntimeOverrides.empty(),
                new ProviderRuntimeConfig(
                        "custom",
                        "saved-local-model",
                        URI.create("https://models.example.test/v1")
                ),
                Map.of(
                        "OPENROUTER_API_KEY", "must-not-leak",
                        "OPENAI_API_KEY", "custom-endpoint-key"
                ),
                "openrouter"
        ));

        assertEquals("custom-endpoint-key", runtime.apiKey());
        assertEquals(URI.create("https://models.example.test/v1"), runtime.baseUrl());
        assertEquals(ProviderResolutionSource.SAVED_CONFIG, runtime.source());
    }

    @Test
    void returnsNativeApiModeFromProviderProfile() {
        ResolvedProviderRuntime runtime = resolver.resolve(new ProviderRuntimeRequest(
                new ProviderRuntimeOverrides("anthropic", "claude-sonnet", null, ""),
                ProviderRuntimeConfig.empty(),
                Map.of("ANTHROPIC_API_KEY", "anthropic-key"),
                "openrouter"
        ));

        assertEquals(ProviderApiMode.ANTHROPIC_MESSAGES, runtime.apiMode());
        assertEquals("anthropic-key", runtime.apiKey());
    }

    @Test
    void environmentDetectionUsesDeclaredProfileOrderWhenSeveralKeysExist() {
        ResolvedProviderRuntime runtime = resolver.resolve(new ProviderRuntimeRequest(
                ProviderRuntimeOverrides.empty(),
                ProviderRuntimeConfig.empty(),
                Map.of(
                        "ANTHROPIC_API_KEY", "anthropic-key",
                        "OPENROUTER_API_KEY", "openrouter-key"
                ),
                "custom"
        ));

        assertEquals("openrouter", runtime.provider());
        assertEquals(ProviderResolutionSource.ENVIRONMENT, runtime.source());
    }

    @Test
    void doesNotExposeResolvedCredentialsInItsStringRepresentation() {
        ResolvedProviderRuntime runtime = resolver.resolve(new ProviderRuntimeRequest(
                new ProviderRuntimeOverrides(
                        "custom",
                        "local-model",
                        URI.create("http://127.0.0.1:11434/v1"),
                        "reader-secret"
                ),
                ProviderRuntimeConfig.empty(),
                Map.of(),
                "openrouter"
        ));

        assertFalse(runtime.toString().contains("reader-secret"));
        assertTrue(runtime.toString().contains("[REDACTED]"));
    }
}
