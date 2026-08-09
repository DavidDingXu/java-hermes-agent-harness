package com.ading.ai.hermes.skill;

import java.util.List;
import java.util.Objects;

public record SkillCandidate(
        String name,
        String description,
        List<String> triggers,
        String instructions,
        SkillProvenance provenance
) {

    public SkillCandidate {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(triggers, "triggers must not be null");
        Objects.requireNonNull(instructions, "instructions must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        name = name.trim();
        description = description.trim();
        instructions = instructions.trim();
        triggers = triggers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(trigger -> !trigger.isBlank())
                .toList();
        if (name.isBlank()) {
            throw new IllegalArgumentException("candidate name must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("candidate description must not be blank");
        }
        if (instructions.isBlank()) {
            throw new IllegalArgumentException("candidate instructions must not be blank");
        }
    }

    public SkillManifest approveAsSkill(String sourceId) {
        return new SkillManifest(
                name,
                description,
                "1.0.0",
                true,
                triggers,
                instructions,
                SkillProvenance.fromContent(SkillSourceKind.LOCAL, sourceId, name, "1.0.0", instructions)
        );
    }
}
