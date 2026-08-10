package com.ading.ai.hermes.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
            "hermes.web.port", "HERMES_WEB_PORT",
            "hermes.profile", "HERMES_PROFILE"
    );

    private LocalApplicationConfiguration() {
    }

    public static Map<String, String> load(Path launchDirectory, Map<String, String> environment) {
        return loadResolved(launchDirectory, environment).values();
    }

    public static LoadedApplicationConfiguration loadResolved(
            Path launchDirectory,
            Map<String, String> environment
    ) {
        Path normalizedLaunchDirectory = normalizeLaunchDirectory(launchDirectory);
        return loadResolved(
                normalizedLaunchDirectory.resolve(DEFAULT_FILE),
                environment,
                false
        );
    }

    public static LoadedApplicationConfiguration loadResolved(
            Path launchDirectory,
            Path configurationFile,
            Map<String, String> environment
    ) {
        Path normalizedLaunchDirectory = normalizeLaunchDirectory(launchDirectory);
        Objects.requireNonNull(configurationFile, "configurationFile must not be null");
        Path resolvedFile = configurationFile.isAbsolute()
                ? configurationFile.toAbsolutePath().normalize()
                : normalizedLaunchDirectory.resolve(configurationFile).normalize();
        return loadResolved(resolvedFile, environment, true);
    }

    private static LoadedApplicationConfiguration loadResolved(
            Path configurationFile,
            Map<String, String> environment,
            boolean requireConfigurationFile
    ) {
        Objects.requireNonNull(environment, "environment must not be null");
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, ConfigurationSource> sources = new LinkedHashMap<>();
        LinkedHashSet<String> ignoredEnvironmentOverrides = new LinkedHashSet<>();
        if (requireConfigurationFile && !Files.isRegularFile(configurationFile)) {
            throw new IllegalArgumentException("本地配置文件不存在: " + configurationFile);
        }
        if (Files.isRegularFile(configurationFile)) {
            Properties properties = read(configurationFile);
            PROPERTY_TO_ENVIRONMENT.forEach((propertyName, environmentName) -> {
                String value = properties.getProperty(propertyName);
                if (hasText(value)) {
                    values.put(environmentName, value.trim());
                    sources.put(environmentName, ConfigurationSource.LOCAL_FILE);
                }
            });
        }
        PROPERTY_TO_ENVIRONMENT.values().forEach(name -> {
            String value = environment.get(name);
            if (hasText(value)) {
                if (sources.get(name) == ConfigurationSource.LOCAL_FILE) {
                    if (!Objects.equals(values.get(name), value.trim())) {
                        ignoredEnvironmentOverrides.add(name);
                    }
                    return;
                }
                values.put(name, value.trim());
                sources.put(name, ConfigurationSource.ENVIRONMENT);
            }
        });
        List<String> notices = ignoredEnvironmentOverrides.isEmpty()
                ? List.of()
                : List.of("本地配置文件优先，已忽略同名环境变量："
                        + String.join("、", ignoredEnvironmentOverrides));
        return new LoadedApplicationConfiguration(values, sources, notices);
    }

    private static Path normalizeLaunchDirectory(Path launchDirectory) {
        return Objects.requireNonNull(
                launchDirectory,
                "launchDirectory must not be null"
        ).toAbsolutePath().normalize();
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
