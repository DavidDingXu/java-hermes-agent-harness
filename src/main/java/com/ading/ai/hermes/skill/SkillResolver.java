package com.ading.ai.hermes.skill;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SkillResolver {

    private final List<SkillManifest> skills;

    public SkillResolver(List<SkillManifest> skills) {
        this.skills = List.copyOf(Objects.requireNonNull(skills, "skills must not be null"));
    }

    public List<SkillManifest> resolve(String task) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task must not be blank");
        }
        String normalizedTask = task.toLowerCase(Locale.ROOT);
        return skills.stream()
                .filter(SkillManifest::enabled)
                .filter(skill -> matches(skill, normalizedTask))
                .toList();
    }

    private boolean matches(SkillManifest skill, String normalizedTask) {
        for (String trigger : skill.triggers()) {
            if (normalizedTask.contains(trigger.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return normalizedTask.contains(skill.name().toLowerCase(Locale.ROOT))
                || normalizedTask.contains(skill.description().toLowerCase(Locale.ROOT));
    }
}
