package com.ading.ai.hermes.model;

import java.util.Objects;

public record PromptCacheDescriptor(String stablePrefixFingerprint, int stablePrefixCharacters) {

    public PromptCacheDescriptor {
        stablePrefixFingerprint = Objects.requireNonNull(
                stablePrefixFingerprint,
                "stablePrefixFingerprint must not be null"
        );
        if (stablePrefixCharacters < 0) {
            throw new IllegalArgumentException("stablePrefixCharacters must not be negative");
        }
        if (stablePrefixCharacters > 0 && stablePrefixFingerprint.isBlank()) {
            throw new IllegalArgumentException("a stable prefix must have a fingerprint");
        }
    }

    public static PromptCacheDescriptor none() {
        return new PromptCacheDescriptor("", 0);
    }
}
