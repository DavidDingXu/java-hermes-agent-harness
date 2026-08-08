package com.ading.ai.hermes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                """);

        Map<String, String> configuration = LocalApplicationConfiguration.load(launchDirectory, Map.of());

        assertEquals("https://local.example", configuration.get("OPENAI_BASE_URL"));
        assertEquals("local-secret", configuration.get("OPENAI_API_KEY"));
        assertEquals("local-model", configuration.get("OPENAI_MODEL"));
        assertEquals("examples", configuration.get("HERMES_WORKSPACE"));
        assertEquals("8181", configuration.get("HERMES_WEB_PORT"));
    }

    @Test
    void environmentOverridesLocalConfiguration() throws Exception {
        Path configDirectory = Files.createDirectories(launchDirectory.resolve("config"));
        Files.writeString(configDirectory.resolve("hermes.local.properties"), """
                openai.model=local-model
                hermes.web.port=8181
                """);

        Map<String, String> configuration = LocalApplicationConfiguration.load(launchDirectory, Map.of(
                "OPENAI_MODEL", "environment-model",
                "HERMES_WEB_PORT", "8282"
        ));

        assertEquals("environment-model", configuration.get("OPENAI_MODEL"));
        assertEquals("8282", configuration.get("HERMES_WEB_PORT"));
    }
}
