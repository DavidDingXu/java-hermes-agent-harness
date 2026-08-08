package com.ading.ai.hermes.web;

import com.ading.ai.hermes.runtime.HermesRuntimeOptions;
import com.ading.ai.hermes.skill.SkillLoader;
import com.ading.ai.hermes.skill.SkillManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record WebRuntimeSettings(
        Path workspace,
        String systemPromptAppendix,
        String projectMemory,
        String userMemory,
        Path skillsDirectory,
        boolean skillsEnabled,
        boolean fileEditingEnabled
) {

    private static final int MAX_TEXT_CHARACTERS = 20_000;
    private static final int MAX_SKILL_INSTRUCTION_CHARACTERS = 100_000;

    public WebRuntimeSettings {
        workspace = Objects.requireNonNull(workspace, "workspace must not be null")
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("workspace must be an existing directory");
        }
        systemPromptAppendix = normalizeText(systemPromptAppendix, "systemPromptAppendix");
        projectMemory = normalizeText(projectMemory, "projectMemory");
        userMemory = normalizeText(userMemory, "userMemory");
        Path configuredSkills = skillsDirectory == null
                ? workspace.resolve(".hermes").resolve("skills")
                : skillsDirectory;
        if (!configuredSkills.isAbsolute()) {
            configuredSkills = workspace.resolve(configuredSkills);
        }
        skillsDirectory = configuredSkills.toAbsolutePath().normalize();
        if (!skillsDirectory.startsWith(workspace)) {
            throw new IllegalArgumentException("skillsDirectory must stay inside the workspace");
        }
        if (Files.exists(skillsDirectory) && !Files.isDirectory(skillsDirectory)) {
            throw new IllegalArgumentException("skillsDirectory must be a directory");
        }
        verifyRealPathBoundary(workspace, skillsDirectory);
        validateSkillSize(loadSkills(skillsDirectory, skillsEnabled));
    }

    public static WebRuntimeSettings defaults(Path workspace) {
        return new WebRuntimeSettings(workspace, "", "", "", null, true, true);
    }

    public List<SkillManifest> loadedSkills() {
        return loadSkills(skillsDirectory, skillsEnabled);
    }

    public HermesRuntimeOptions toRuntimeOptions() {
        return new HermesRuntimeOptions(
                40_000,
                100_000,
                fileEditingEnabled,
                systemPromptAppendix,
                projectMemory,
                userMemory,
                loadedSkills()
        );
    }

    private static List<SkillManifest> loadSkills(Path directory, boolean enabled) {
        return enabled ? new SkillLoader().loadAll(directory) : List.of();
    }

    private static void validateSkillSize(List<SkillManifest> skills) {
        int characters = skills.stream().mapToInt(skill -> skill.instructions().length()).sum();
        if (characters > MAX_SKILL_INSTRUCTION_CHARACTERS) {
            throw new IllegalArgumentException(
                    "loaded skill instructions must be at most "
                            + MAX_SKILL_INSTRUCTION_CHARACTERS + " characters"
            );
        }
    }

    private static void verifyRealPathBoundary(Path workspace, Path skillsDirectory) {
        if (!Files.exists(skillsDirectory)) {
            return;
        }
        try {
            if (!skillsDirectory.toRealPath().startsWith(workspace.toRealPath())) {
                throw new IllegalArgumentException("skillsDirectory must stay inside the workspace");
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to resolve skillsDirectory", error);
        }
    }

    private static String normalizeText(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_TEXT_CHARACTERS) {
            throw new IllegalArgumentException(
                    name + " must be at most " + MAX_TEXT_CHARACTERS + " characters"
            );
        }
        return normalized;
    }
}
