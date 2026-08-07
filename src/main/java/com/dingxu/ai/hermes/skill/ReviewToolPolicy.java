package com.dingxu.ai.hermes.skill;

import java.util.Objects;
import java.util.Set;

public final class ReviewToolPolicy {

    private final Set<String> allowedTools;

    private ReviewToolPolicy(Set<String> allowedTools) {
        this.allowedTools = Set.copyOf(allowedTools);
    }

    public static ReviewToolPolicy memoryAndSkillsOnly() {
        return new ReviewToolPolicy(Set.of(
                "memory",
                "skill_manage",
                "skill_view",
                "skills_list"
        ));
    }

    public boolean allows(String toolName) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        return allowedTools.contains(toolName.trim());
    }

    public Set<String> allowedTools() {
        return allowedTools;
    }
}
