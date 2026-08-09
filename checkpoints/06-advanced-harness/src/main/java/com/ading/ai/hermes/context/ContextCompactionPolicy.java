package com.ading.ai.hermes.context;

public record ContextCompactionPolicy(
        int maxCharacters,
        int keepFirstEvents,
        int keepLastEvents,
        int summaryMaxCharacters,
        int eventPreviewMaxCharacters
) {

    public ContextCompactionPolicy {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("maxCharacters must be positive");
        }
        if (keepFirstEvents < 0 || keepLastEvents < 0) {
            throw new IllegalArgumentException("protected event counts must not be negative");
        }
        if (summaryMaxCharacters <= 0) {
            throw new IllegalArgumentException("summaryMaxCharacters must be positive");
        }
        if (eventPreviewMaxCharacters <= 0) {
            throw new IllegalArgumentException("eventPreviewMaxCharacters must be positive");
        }
    }
}
