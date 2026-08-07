package com.ading.ai.hermes.examples.coding;

import java.util.Objects;

public record CodingPatch(String path, String expected, String replacement) {

    public CodingPatch {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(replacement, "replacement must not be null");
        path = path.trim();
        if (path.isBlank()) {
            throw new IllegalArgumentException("patch path must not be blank");
        }
    }
}
