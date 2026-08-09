package com.ading.ai.hermes.web;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebRuntimeConfigTest {

    @TempDir
    Path workspace;

    @Test
    void normalizesAnExistingWorkspace() {
        WebRuntimeConfig config = new WebRuntimeConfig(
                "https://models.example",
                "secret",
                "hermes-model",
                workspace.resolve(".")
        );

        assertEquals(workspace.toAbsolutePath().normalize(), config.workspace());
    }

    @Test
    void rejectsAMissingWorkspace() {
        Path missing = workspace.resolve("missing");

        assertThrows(IllegalArgumentException.class, () -> new WebRuntimeConfig(
                "https://models.example",
                "secret",
                "hermes-model",
                missing
        ));
    }

    @Test
    void doesNotExposeTheApiKeyInItsStringRepresentation() {
        WebRuntimeConfig config = new WebRuntimeConfig(
                "https://models.example",
                "reader-secret",
                "hermes-model",
                workspace
        );

        assertFalse(config.toString().contains("reader-secret"));
        assertTrue(config.toString().contains("[REDACTED]"));
    }
}
