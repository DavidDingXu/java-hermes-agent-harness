package com.dingxu.ai.hermes.model;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record OpenAiCompatibleOptions(URI baseUrl, String apiKey, Duration timeout) {

    public OpenAiCompatibleOptions {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static OpenAiCompatibleOptions of(String baseUrl, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        return new OpenAiCompatibleOptions(URI.create(baseUrl), apiKey, Duration.ofSeconds(30));
    }

    URI chatCompletionsUri() {
        String normalized = baseUrl.toString();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            return URI.create(normalized + "/chat/completions");
        }
        return URI.create(normalized + "/v1/chat/completions");
    }
}
