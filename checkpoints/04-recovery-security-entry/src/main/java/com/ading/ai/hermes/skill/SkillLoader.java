package com.ading.ai.hermes.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SkillLoader {

    public SkillManifest load(Path skillDir) {
        Objects.requireNonNull(skillDir, "skillDir must not be null");
        Path skillFile = skillDir.resolve("SKILL.md");
        try {
            return parse(Files.readString(skillFile, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new SkillLoadException("failed to load skill: " + skillFile, error);
        }
    }

    public List<SkillManifest> loadAll(Path skillsDir) {
        Objects.requireNonNull(skillsDir, "skillsDir must not be null");
        if (!Files.isDirectory(skillsDir)) {
            return List.of();
        }
        try (var stream = Files.list(skillsDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("SKILL.md")))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::load)
                    .toList();
        } catch (IOException error) {
            throw new SkillLoadException("failed to list skills: " + skillsDir, error);
        }
    }

    private SkillManifest parse(String content) {
        if (!content.startsWith("---\n")) {
            throw new IllegalArgumentException("skill frontmatter is required");
        }
        int end = content.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("skill frontmatter is not closed");
        }

        Map<String, String> fields = parseFields(content.substring(4, end));
        String name = fields.getOrDefault("name", "");
        if (name.isBlank()) {
            throw new IllegalArgumentException("skill name is required");
        }

        String body = content.substring(end + 4);
        return new SkillManifest(
                name,
                fields.getOrDefault("description", ""),
                fields.getOrDefault("version", "1.0.0"),
                Boolean.parseBoolean(fields.getOrDefault("enabled", "true")),
                parseList(fields.getOrDefault("triggers", "")),
                body
        );
    }

    private Map<String, String> parseFields(String frontmatter) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : frontmatter.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || !trimmed.contains(":")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            fields.put(key, stripQuotes(value));
        }
        return fields;
    }

    private List<String> parseList(String raw) {
        String value = raw.trim();
        if (value.isBlank()) {
            return List.of();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String cleaned = stripQuotes(item.trim());
            if (!cleaned.isBlank()) {
                items.add(cleaned);
            }
        }
        return List.copyOf(items);
    }

    private String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
