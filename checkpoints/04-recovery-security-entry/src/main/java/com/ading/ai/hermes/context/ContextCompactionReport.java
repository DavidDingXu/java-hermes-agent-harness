package com.ading.ai.hermes.context;

public record ContextCompactionReport(
        int originalEvents,
        int retainedEvents,
        int summarizedEvents,
        int originalCharacters,
        int compactedCharacters
) {

    public ContextCompactionReport {
        if (originalEvents < 0 || retainedEvents < 0 || summarizedEvents < 0) {
            throw new IllegalArgumentException("event counts must not be negative");
        }
        if (originalCharacters < 0 || compactedCharacters < 0) {
            throw new IllegalArgumentException("character counts must not be negative");
        }
    }
}
