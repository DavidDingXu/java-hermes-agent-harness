package com.ading.ai.hermes.learning;

import java.util.List;

public record LearningSkill(String id, String name, String description, List<String> relatedSkillIds) {

    public LearningSkill {
        id = requireText(id, "skill id");
        name = requireText(name, "skill name");
        description = requireText(description, "skill description");
        relatedSkillIds = relatedSkillIds == null ? List.of() : relatedSkillIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
