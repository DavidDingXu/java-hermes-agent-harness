package com.ading.ai.hermes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalApplicationConfigurationTest {

    @TempDir
    Path launchDirectory;

    @Test
    void readsIgnoredProjectLocalConfiguration() throws Exception {
        Path configDirectory = Files.createDirectories(launchDirectory.resolve("config"));
        Files.writeString(configDirectory.resolve("hermes.local.properties"), """
                openai.base-url=https://local.example
                openai.api-key=local-secret
                openai.model=local-model
                hermes.workspace=examples
                hermes.web.port=8181
                hermes.profile=writer
                """);

        Map<String, String> configuration = LocalApplicationConfiguration.load(launchDirectory, Map.of());

        assertEquals("https://local.example", configuration.get("OPENAI_BASE_URL"));
        assertEquals("local-secret", configuration.get("OPENAI_API_KEY"));
        assertEquals("local-model", configuration.get("OPENAI_MODEL"));
        assertEquals("examples", configuration.get("HERMES_WORKSPACE"));
        assertEquals("8181", configuration.get("HERMES_WEB_PORT"));
        assertEquals("writer", configuration.get("HERMES_PROFILE"));
    }

    @Test
    void localConfigurationWinsAndEnvironmentFillsMissingValues() throws Exception {
        Path configDirectory = Files.createDirectories(launchDirectory.resolve("config"));
        Files.writeString(configDirectory.resolve("hermes.local.properties"), """
                openai.model=local-model
                hermes.web.port=8181
                """);

        Map<String, String> configuration = LocalApplicationConfiguration.load(launchDirectory, Map.of(
                "OPENAI_BASE_URL", "https://environment.example",
                "OPENAI_API_KEY", "environment-secret",
                "OPENAI_MODEL", "environment-model",
                "HERMES_WEB_PORT", "8282"
        ));

        assertEquals("https://environment.example", configuration.get("OPENAI_BASE_URL"));
        assertEquals("environment-secret", configuration.get("OPENAI_API_KEY"));
        assertEquals("local-model", configuration.get("OPENAI_MODEL"));
        assertEquals("8181", configuration.get("HERMES_WEB_PORT"));
    }

    @Test
    void reportsIgnoredEnvironmentOverridesWithoutExposingValues() throws Exception {
        Path configDirectory = Files.createDirectories(launchDirectory.resolve("config"));
        Files.writeString(configDirectory.resolve("hermes.local.properties"), """
                openai.base-url=https://local.example
                openai.api-key=local-secret
                openai.model=local-model
                """);

        LoadedApplicationConfiguration configuration = LocalApplicationConfiguration.loadResolved(
                launchDirectory,
                Map.of(
                        "OPENAI_BASE_URL", "https://environment.example",
                        "OPENAI_API_KEY", "environment-secret",
                        "OPENAI_MODEL", "environment-model"
                )
        );

        assertEquals(ConfigurationSource.LOCAL_FILE, configuration.modelSource());
        assertEquals(ConfigurationSource.LOCAL_FILE, configuration.sourceOf("OPENAI_API_KEY"));
        assertEquals("local-model", configuration.values().get("OPENAI_MODEL"));
        assertTrue(configuration.notices().stream()
                .anyMatch(notice -> notice.contains("本地配置") && notice.contains("已忽略")
                        && notice.contains("OPENAI_API_KEY")));
        String text = configuration.toString() + configuration.notices();
        assertFalse(text.contains("local-secret"));
        assertFalse(text.contains("environment-secret"));
    }
}
