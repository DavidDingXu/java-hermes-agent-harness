package com.ading.ai.hermes.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class LocalApplicationConfiguration {

    public static final Path DEFAULT_FILE = Path.of("config", "hermes.local.properties");

    private static final Map<String, String> PROPERTY_TO_ENVIRONMENT = Map.of(
            "openai.base-url", "OPENAI_BASE_URL",
            "openai.api-key", "OPENAI_API_KEY",
            "openai.model", "OPENAI_MODEL",
            "hermes.workspace", "HERMES_WORKSPACE",
            "hermes.web.port", "HERMES_WEB_PORT"
    );

    private LocalApplicationConfiguration() {
    }

    public static Map<String, String> load(Path launchDirectory, Map<String, String> environment) {
        Objects.requireNonNull(launchDirectory, "launchDirectory must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Map<String, String> values = new LinkedHashMap<>();
        Path configurationFile = launchDirectory.toAbsolutePath().normalize().resolve(DEFAULT_FILE);
        if (Files.isRegularFile(configurationFile)) {
            Properties properties = read(configurationFile);
            PROPERTY_TO_ENVIRONMENT.forEach((propertyName, environmentName) -> {
                String value = properties.getProperty(propertyName);
                if (hasText(value)) {
                    values.put(environmentName, value.trim());
                }
            });
        }
        environment.forEach((name, value) -> {
            if (hasText(value)) {
                values.put(name, value.trim());
            }
        });
        return Map.copyOf(values);
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException error) {
            throw new IllegalStateException("读取本地配置失败: " + file, error);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
