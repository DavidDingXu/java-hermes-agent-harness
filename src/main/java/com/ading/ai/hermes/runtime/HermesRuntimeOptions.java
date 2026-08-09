package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.prompt.PromptPolicy;
import com.ading.ai.hermes.skill.SkillManifest;
import com.ading.ai.hermes.skill.SkillResolver;
import com.ading.ai.hermes.skill.SkillTrustAction;
import com.ading.ai.hermes.skill.TrustedSkillPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record HermesRuntimeOptions(
        int maxFileCharacters,
        int maxReferencedContextCharacters,
        boolean fileEditingEnabled,
        String systemPromptAppendix,
        String projectMemory,
        String userMemory,
        List<SkillManifest> skills
) {

    private static final int MAX_CONFIGURED_TEXT_CHARACTERS = 40_000;

    public HermesRuntimeOptions {
        if (maxFileCharacters <= 0) {
            throw new IllegalArgumentException("maxFileCharacters must be positive");
        }
        if (maxReferencedContextCharacters <= 0) {
            throw new IllegalArgumentException("maxReferencedContextCharacters must be positive");
        }
        systemPromptAppendix = normalizeText(systemPromptAppendix, "systemPromptAppendix");
        projectMemory = normalizeText(projectMemory, "projectMemory");
        userMemory = normalizeText(userMemory, "userMemory");
        skills = List.copyOf(Objects.requireNonNull(skills, "skills must not be null"));
    }

    public static HermesRuntimeOptions defaults() {
        return new HermesRuntimeOptions(40_000, 100_000, true, "", "", "", List.of());
    }

    public String systemPromptFor(String task) {
        return systemPromptFor(task, List.of(), List.of(), List.of());
    }

    public String systemPromptFor(
            String task,
            List<String> learnedProjectMemory,
            List<String> learnedUserMemory,
            List<SkillManifest> approvedSkills
    ) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task must not be blank");
        }
        Objects.requireNonNull(learnedProjectMemory, "learnedProjectMemory must not be null");
        Objects.requireNonNull(learnedUserMemory, "learnedUserMemory must not be null");
        Objects.requireNonNull(approvedSkills, "approvedSkills must not be null");
        List<String> sections = new ArrayList<>();
        sections.add(PromptPolicy.hermesDefault().systemPrompt());
        addSection(sections, "Additional runtime rules", systemPromptAppendix);
        addSection(sections, "Project memory", mergeMemory(projectMemory, learnedProjectMemory));
        addSection(sections, "User memory", mergeMemory(userMemory, learnedUserMemory));

        Map<String, SkillManifest> availableSkills = new LinkedHashMap<>();
        skills.forEach(skill -> availableSkills.put(skill.name(), skill));
        approvedSkills.forEach(skill -> availableSkills.put(skill.name(), skill));
        List<SkillManifest> activeSkills = new SkillResolver(List.copyOf(availableSkills.values()))
                .resolve(task).stream()
                .filter(skill -> TrustedSkillPolicy.defaultPolicy().evaluate(skill).action()
                        == SkillTrustAction.ALLOW)
                .toList();
        for (SkillManifest skill : activeSkills) {
            addSection(
                    sections,
                    "Active skill: " + skill.name() + " (version " + skill.version() + ")",
                    skill.instructions()
            );
        }
        return String.join("\n\n", sections);
    }

    private static String mergeMemory(String configured, List<String> learned) {
        List<String> entries = new ArrayList<>();
        if (!configured.isBlank()) {
            entries.add(configured);
        }
        learned.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .filter(entry -> !entries.contains(entry))
                .forEach(entries::add);
        return String.join("\n", entries);
    }

    private static void addSection(List<String> sections, String title, String content) {
        if (!content.isBlank()) {
            sections.add("## " + title + "\n" + content);
        }
    }

    private static String normalizeText(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_CONFIGURED_TEXT_CHARACTERS) {
            throw new IllegalArgumentException(
                    name + " must be at most " + MAX_CONFIGURED_TEXT_CHARACTERS + " characters"
            );
        }
        return normalized;
    }
}
