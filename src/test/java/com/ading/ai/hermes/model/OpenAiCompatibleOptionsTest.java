package com.ading.ai.hermes.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleOptionsTest {

    @Test
    void doesNotExposeTheApiKeyInItsStringRepresentation() {
        OpenAiCompatibleOptions options = OpenAiCompatibleOptions.of(
                "https://models.example",
                "reader-secret"
        );

        assertFalse(options.toString().contains("reader-secret"));
        assertTrue(options.toString().contains("[REDACTED]"));
    }
}
