package com.ading.ai.hermes.cli;

import com.ading.ai.hermes.config.ConfigurationSource;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

final class CliStartupConfiguration {

    private CliStartupConfiguration() {
    }

    static ModelConfiguration resolveModel(Map<String, String> environment, PromptInput input) {
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(input, "input must not be null");
        int configuredValues = countConfiguredModelValues(environment);
        return new ModelConfiguration(
                valueOrPrompt(environment.get("OPENAI_BASE_URL"), "Base URL", input::readLine),
                valueOrPrompt(environment.get("OPENAI_API_KEY"), "API Key", input::readSecret),
                valueOrPrompt(environment.get("OPENAI_MODEL"), "模型", input::readLine),
                sourceFor(configuredValues, ConfigurationSource.ENVIRONMENT)
        );
    }

    static ModelConfiguration resolveModel(
            Map<String, String> configuration,
            ConfigurationSource configuredSource,
            PromptInput input
    ) {
        Objects.requireNonNull(configuredSource, "configuredSource must not be null");
        ModelConfiguration resolved = resolveModel(configuration, input);
        int configuredValues = countConfiguredModelValues(configuration);
        return new ModelConfiguration(
                resolved.baseUrl(),
                resolved.apiKey(),
                resolved.model(),
                sourceFor(configuredValues, configuredSource)
        );
    }

    static String[] addPromptWhenMissing(String[] args, PromptInput input) {
        Objects.requireNonNull(args, "args must not be null");
        Objects.requireNonNull(input, "input must not be null");
        if (containsPromptOption(args)) {
            return args;
        }
        String task = required(input.readLine("任务: "), "任务");
        String[] effectiveArgs = Arrays.copyOf(args, args.length + 2);
        effectiveArgs[args.length] = "--prompt";
        effectiveArgs[args.length + 1] = task;
        return effectiveArgs;
    }

    private static String valueOrPrompt(String configured, String name, ValueReader reader) {
        if (hasText(configured)) {
            return configured.trim();
        }
        return required(reader.read(name + ": "), name);
    }

    private static String required(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    private static boolean containsPromptOption(String[] args) {
        return Arrays.asList(args).contains("--prompt");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int countConfiguredModelValues(Map<String, String> values) {
        int count = 0;
        count += hasText(values.get("OPENAI_BASE_URL")) ? 1 : 0;
        count += hasText(values.get("OPENAI_API_KEY")) ? 1 : 0;
        count += hasText(values.get("OPENAI_MODEL")) ? 1 : 0;
        return count;
    }

    private static ConfigurationSource sourceFor(
            int configuredValues,
            ConfigurationSource configuredSource
    ) {
        if (configuredValues == 0) {
            return ConfigurationSource.INTERACTIVE;
        }
        if (configuredValues < 3) {
            return ConfigurationSource.MIXED;
        }
        return configuredSource == ConfigurationSource.UNCONFIGURED
                ? ConfigurationSource.ENVIRONMENT
                : configuredSource;
    }

    record ModelConfiguration(
            String baseUrl,
            String apiKey,
            String model,
            ConfigurationSource source
    ) {

        ModelConfiguration {
            Objects.requireNonNull(source, "source must not be null");
        }

        @Override
        public String toString() {
            return "ModelConfiguration[baseUrl=" + baseUrl
                    + ", apiKey=[REDACTED], model=" + model
                    + ", source=" + source + "]";
        }
    }

    interface PromptInput {

        String readLine(String prompt);

        String readSecret(String prompt);
    }

    @FunctionalInterface
    private interface ValueReader {

        String read(String prompt);
    }
}
