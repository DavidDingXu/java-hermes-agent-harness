package com.ading.ai.hermes.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LoadedApplicationConfiguration {

    private static final List<String> MODEL_KEYS = List.of(
            "OPENAI_BASE_URL",
            "OPENAI_API_KEY",
            "OPENAI_MODEL"
    );

    private final Map<String, String> values;
    private final Map<String, ConfigurationSource> sources;
    private final List<String> notices;

    public LoadedApplicationConfiguration(
            Map<String, String> values,
            Map<String, ConfigurationSource> sources,
            List<String> notices
    ) {
        this.values = Map.copyOf(Objects.requireNonNull(values, "values must not be null"));
        this.sources = Map.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
        this.notices = List.copyOf(Objects.requireNonNull(notices, "notices must not be null"));
    }

    public Map<String, String> values() {
        return values;
    }

    public ConfigurationSource sourceOf(String name) {
        return sources.getOrDefault(name, ConfigurationSource.UNCONFIGURED);
    }

    public ConfigurationSource modelSource() {
        Set<ConfigurationSource> modelSources = new LinkedHashSet<>();
        MODEL_KEYS.stream()
                .filter(values::containsKey)
                .map(this::sourceOf)
                .filter(source -> source != ConfigurationSource.UNCONFIGURED)
                .forEach(modelSources::add);
        if (modelSources.isEmpty()) {
            return ConfigurationSource.UNCONFIGURED;
        }
        return modelSources.size() == 1
                ? modelSources.iterator().next()
                : ConfigurationSource.MIXED;
    }

    public List<String> notices() {
        return notices;
    }

    @Override
    public String toString() {
        return "LoadedApplicationConfiguration[keys=" + values.keySet()
                + ", sources=" + sources + ", notices=" + notices + "]";
    }
}
