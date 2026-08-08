package com.ading.ai.hermes.verification;

import com.ading.ai.hermes.terminal.TerminalResult;
import com.ading.ai.hermes.terminal.TerminalStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectVerificationTest {

    @TempDir
    Path projectRoot;

    @Test
    void detectsAMavenProjectWithoutExecutingIt() throws Exception {
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");

        ProjectVerificationRecipe recipe = new JavaProjectVerificationDetector()
                .detect(projectRoot)
                .orElseThrow();

        assertEquals("Maven", recipe.name());
        assertEquals(projectRoot.toAbsolutePath().normalize(), recipe.projectRoot());
        assertEquals("test", recipe.commands().getFirst().phase());
        assertEquals("test", recipe.commands().getFirst().argv().getLast());
        assertEquals(List.of("Detected pom.xml"), recipe.evidence());
    }

    @Test
    void returnsEmptyWhenNoSupportedBuildManifestExists() {
        assertTrue(new JavaProjectVerificationDetector().detect(projectRoot).isEmpty());
    }

    @Test
    void stopsAtTheFirstFailedVerificationPhase() {
        List<List<String>> invoked = new ArrayList<>();
        List<Path> workingDirectories = new ArrayList<>();
        ProjectVerificationRunner runner = new ProjectVerificationRunner(command -> {
            invoked.add(command.argv());
            workingDirectories.add(command.workingDirectory());
            if (invoked.size() == 1) {
                return new TerminalResult(TerminalStatus.FAILED, "test failed", 1, false);
            }
            return new TerminalResult(TerminalStatus.SUCCESS, "unused", 0, false);
        }, Duration.ofSeconds(5));
        ProjectVerificationRecipe recipe = new ProjectVerificationRecipe(
                "Java",
                projectRoot,
                List.of(
                        new ProjectVerificationCommand("test", List.of("mvn", "test")),
                        new ProjectVerificationCommand("package", List.of("mvn", "package"))
                ),
                List.of("test recipe")
        );

        ProjectVerificationResult result = runner.run(recipe);

        assertFalse(result.passed());
        assertFalse(result.asCompletionEvidence().accepted());
        assertEquals(1, result.steps().size());
        assertEquals(List.of(List.of("mvn", "test")), invoked);
        assertEquals(List.of(projectRoot.toAbsolutePath().normalize()), workingDirectories);
    }

    @Test
    void convertsSuccessfulCommandsIntoCompletionEvidence() {
        ProjectVerificationRunner runner = new ProjectVerificationRunner(
                command -> new TerminalResult(TerminalStatus.SUCCESS, "BUILD SUCCESS", 0, false),
                Duration.ofSeconds(5)
        );
        ProjectVerificationRecipe recipe = new ProjectVerificationRecipe(
                "Maven",
                projectRoot,
                List.of(new ProjectVerificationCommand("test", List.of("mvn", "test"))),
                List.of("Detected pom.xml")
        );

        ProjectVerificationResult result = runner.run(recipe);

        assertTrue(result.passed());
        assertTrue(result.asCompletionEvidence().accepted());
        assertEquals("all project verification commands passed", result.asCompletionEvidence().detail());
    }
}
