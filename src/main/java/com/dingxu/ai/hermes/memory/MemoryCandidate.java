package com.dingxu.ai.hermes.memory;

import java.util.Objects;

public record MemoryCandidate(String text, Source source) {

    public enum Source {
        USER_TEXT,
        OBSERVATION
    }

    public MemoryCandidate {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(source, "source must not be null");
        text = text.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }

    public static MemoryCandidate fromUserText(String text) {
        return new MemoryCandidate(text, Source.USER_TEXT);
    }

    public static MemoryCandidate fromObservation(String text) {
        return new MemoryCandidate(text, Source.OBSERVATION);
    }
}
