package com.ading.ai.hermes.web;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRuntimeSettingsTest {

    @TempDir
    Path workspace;

    @Test
    void defaultsKeepSkillsInsideTheWorkspaceAndEnableReaderTools() {
        WebRuntimeSettings settings = WebRuntimeSettings.defaults(workspace);

        assertEquals(workspace.resolve(".hermes/skills").toAbsolutePath(), settings.skillsDirectory());
        assertTrue(settings.skillsEnabled());
        assertTrue(settings.fileEditingEnabled());
        assertEquals(0, settings.loadedSkills().size());
    }

    @Test
    void loadsSkillsFromARelativeWorkspaceDirectory() throws Exception {
        Path skill = workspace.resolve("skills/reader-summary");
        Files.createDirectories(skill);
        Files.writeString(skill.resolve("SKILL.md"), """
                ---
                name: reader-summary
                triggers: [summary]
                ---

                Lead with the conclusion.
                """);

        WebRuntimeSettings settings = new WebRuntimeSettings(
                workspace, "", "", "", Path.of("skills"), true, false
        );

        assertEquals("reader-summary", settings.loadedSkills().getFirst().name());
        assertFalse(settings.toRuntimeOptions().fileEditingEnabled());
    }

    @Test
    void rejectsSkillsDirectoryOutsideTheWorkspace() {
        assertThrows(IllegalArgumentException.class, () -> new WebRuntimeSettings(
                workspace,
                "",
                "",
                "",
                workspace.resolve("../outside"),
                true,
                true
        ));
    }
}
