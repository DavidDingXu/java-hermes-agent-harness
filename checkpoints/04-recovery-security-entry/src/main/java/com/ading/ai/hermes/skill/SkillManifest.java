package com.ading.ai.hermes.skill;

import java.util.List;
import java.util.Objects;

public record SkillManifest(
        String name,
        String description,
        String version,
        boolean enabled,
        List<String> triggers,
        String instructions,
        SkillProvenance provenance
) {

    public SkillManifest(
            String name,
            String description,
            String version,
            boolean enabled,
            List<String> triggers,
            String instructions
    ) {
        this(
                name,
                description,
                version,
                enabled,
                triggers,
                instructions,
                SkillProvenance.fromContent(
                        SkillSourceKind.LOCAL,
                        "local/" + name,
                        name,
                        version,
                        normalizeInstructions(Objects.requireNonNull(instructions, "instructions must not be null"))
                )
        );
    }

    public SkillManifest {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(triggers, "triggers must not be null");
        Objects.requireNonNull(instructions, "instructions must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        name = name.trim();
        description = description.trim();
        version = version.trim();
        instructions = normalizeInstructions(instructions);
        triggers = triggers.stream()
                .map(String::trim)
                .filter(trigger -> !trigger.isBlank())
                .toList();
        if (name.isBlank()) {
            throw new IllegalArgumentException("skill name is required");
        }
    }

    private static String normalizeInstructions(String instructions) {
        String text = instructions.trim();
        if (text.startsWith("# ")) {
            int firstBlankLine = text.indexOf("\n\n");
            if (firstBlankLine >= 0 && firstBlankLine + 2 < text.length()) {
                return text.substring(firstBlankLine + 2).trim();
            }
        }
        return text;
    }
}
