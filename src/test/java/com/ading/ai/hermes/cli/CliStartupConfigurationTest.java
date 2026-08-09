package com.ading.ai.hermes.cli;

import com.ading.ai.hermes.config.ConfigurationSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliStartupConfigurationTest {

    @Test
    void usesConfiguredModelWithoutPrompting() {
        RecordingInput input = new RecordingInput();

        var model = CliStartupConfiguration.resolveModel(Map.of(
                "OPENAI_BASE_URL", "https://models.example",
                "OPENAI_API_KEY", "secret",
                "OPENAI_MODEL", "hermes-model"
        ), input);

        assertEquals("https://models.example", model.baseUrl());
        assertEquals("secret", model.apiKey());
        assertEquals("hermes-model", model.model());
        assertEquals(ConfigurationSource.ENVIRONMENT, model.source());
        assertEquals(List.of(), input.prompts);
    }

    @Test
    void promptsOnlyForMissingModelValues() {
        RecordingInput input = new RecordingInput("https://models.example", "secret");

        var model = CliStartupConfiguration.resolveModel(Map.of(
                "OPENAI_MODEL", "hermes-model"
        ), input);

        assertEquals("https://models.example", model.baseUrl());
        assertEquals("secret", model.apiKey());
        assertEquals(ConfigurationSource.MIXED, model.source());
        assertEquals(List.of("Base URL: ", "API Key: "), input.prompts);
        assertEquals(List.of(false, true), input.secretPrompts);
    }

    @Test
    void reportsInteractiveSourceWhenAllModelValuesAreEnteredAtStartup() {
        RecordingInput input = new RecordingInput(
                "https://models.example",
                "secret",
                "hermes-model"
        );

        var model = CliStartupConfiguration.resolveModel(Map.of(), input);

        assertEquals(ConfigurationSource.INTERACTIVE, model.source());
    }

    @Test
    void appendsInteractiveTaskWhenPromptOptionIsMissing() {
        RecordingInput input = new RecordingInput("检查项目边界");

        String[] effectiveArgs = CliStartupConfiguration.addPromptWhenMissing(
                new String[]{"--max-turns", "4"},
                input
        );

        assertArrayEquals(
                new String[]{"--max-turns", "4", "--prompt", "检查项目边界"},
                effectiveArgs
        );
    }

    @Test
    void keepsExistingPromptAndRejectsBlankInteractiveValues() {
        String[] args = {"--prompt", "检查项目"};
        assertArrayEquals(args, CliStartupConfiguration.addPromptWhenMissing(args, new RecordingInput()));

        RecordingInput blankInput = new RecordingInput(" ");
        assertThrows(
                IllegalArgumentException.class,
                () -> CliStartupConfiguration.addPromptWhenMissing(new String[0], blankInput)
        );
    }

    @Test
    void doesNotExposeTheApiKeyInItsStringRepresentation() {
        var model = CliStartupConfiguration.resolveModel(Map.of(
                "OPENAI_BASE_URL", "https://models.example",
                "OPENAI_API_KEY", "reader-secret",
                "OPENAI_MODEL", "hermes-model"
        ), new RecordingInput());

        assertFalse(model.toString().contains("reader-secret"));
        assertTrue(model.toString().contains("[REDACTED]"));
    }

    private static final class RecordingInput implements CliStartupConfiguration.PromptInput {

        private final Queue<String> answers = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();
        private final List<Boolean> secretPrompts = new ArrayList<>();

        private RecordingInput(String... answers) {
            this.answers.addAll(List.of(answers));
        }

        @Override
        public String readLine(String prompt) {
            prompts.add(prompt);
            secretPrompts.add(false);
            return answers.poll();
        }

        @Override
        public String readSecret(String prompt) {
            prompts.add(prompt);
            secretPrompts.add(true);
            return answers.poll();
        }
    }
}
