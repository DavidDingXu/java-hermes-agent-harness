package com.ading.ai.hermes.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillLoaderTest {

    @TempDir
    Path skillsDir;

    @Test
    void loadsSkillManifestFromSkillMarkdown() throws Exception {
        Path skillDir = skillsDir.resolve("java-testing");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: java-testing
                description: Run focused Java tests before broad verification
                version: 1.0.0
                enabled: true
                triggers: [test, maven, junit]
                ---

                # Java Testing

                Run the narrowest Maven test first, then run the full module test.
                """);

        SkillLoader loader = new SkillLoader();

        SkillManifest skill = loader.load(skillDir);

        assertEquals("java-testing", skill.name());
        assertEquals("Run focused Java tests before broad verification", skill.description());
        assertEquals("1.0.0", skill.version());
        assertEquals(true, skill.enabled());
        assertEquals(List.of("test", "maven", "junit"), skill.triggers());
        assertEquals("Run the narrowest Maven test first, then run the full module test.", skill.instructions());
    }

    @Test
    void rejectsSkillWithoutName() throws Exception {
        Path skillDir = skillsDir.resolve("broken");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Missing name
                ---

                # Broken
                """);

        SkillLoader loader = new SkillLoader();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> loader.load(skillDir));

        assertEquals("skill name is required", error.getMessage());
    }

    @Test
    void loadsAllSkillsFromImmediateSubdirectories() throws Exception {
        Path testing = skillsDir.resolve("java-testing");
        Path writing = skillsDir.resolve("article-writing");
        Files.createDirectories(testing);
        Files.createDirectories(writing);
        Files.writeString(testing.resolve("SKILL.md"), """
                ---
                name: java-testing
                description: Run Java tests
                triggers: [test]
                ---

                Run Maven tests.
                """);
        Files.writeString(writing.resolve("SKILL.md"), """
                ---
                name: article-writing
                description: Write long engineering articles
                enabled: false
                triggers: [article]
                ---

                Draft with concrete code evidence.
                """);

        SkillLoader loader = new SkillLoader();

        List<SkillManifest> skills = loader.loadAll(skillsDir);

        assertEquals(List.of("article-writing", "java-testing"), skills.stream().map(SkillManifest::name).toList());
    }

    @Test
    void resolvesEnabledSkillByTriggerOrDescription() {
        SkillManifest testing = new SkillManifest(
                "java-testing",
                "Run focused Java tests",
                "1.0.0",
                true,
                List.of("maven", "junit"),
                "Run Maven tests."
        );
        SkillManifest disabled = new SkillManifest(
                "article-writing",
                "Write long engineering articles",
                "1.0.0",
                false,
                List.of("article"),
                "Draft articles."
        );
        SkillResolver resolver = new SkillResolver(List.of(testing, disabled));

        List<SkillManifest> matches = resolver.resolve("please run the maven test for this module");
        List<SkillManifest> disabledMatches = resolver.resolve("write article draft");

        assertEquals(List.of(testing), matches);
        assertEquals(List.of(), disabledMatches);
    }

    @Test
    void resolverRejectsBlankTask() {
        SkillResolver resolver = new SkillResolver(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(" "));

        assertEquals("task must not be blank", error.getMessage());
    }

    @Test
    void emptyDescriptionDoesNotMatchEveryTask() {
        SkillManifest skill = new SkillManifest(
                "focused-review",
                "",
                "1.0.0",
                true,
                List.of("review this change"),
                "Inspect the diff."
        );

        List<SkillManifest> matches = new SkillResolver(List.of(skill)).resolve("summarize README");

        assertEquals(List.of(), matches);
    }
}
