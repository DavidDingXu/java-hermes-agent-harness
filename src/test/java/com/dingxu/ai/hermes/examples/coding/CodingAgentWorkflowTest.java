package com.dingxu.ai.hermes.examples.coding;

import com.dingxu.ai.hermes.core.ModelTurn;
import com.dingxu.ai.hermes.model.ChatResponse;
import com.dingxu.ai.hermes.model.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodingAgentWorkflowTest {

    @TempDir
    Path workspace;

    @Test
    void appliesModelPatchAndRunsVerificationInsideWorkspace() throws Exception {
        Files.writeString(workspace.resolve("Calculator.java"), "return a - b;\n", StandardCharsets.UTF_8);
        ModelProvider model = request -> ChatResponse.of(ModelTurn.finalAnswer("""
                {
                  "summary": "Fix addition bug",
                  "patches": [
                    {
                      "path": "Calculator.java",
                      "expected": "return a - b;",
                      "replacement": "return a + b;"
                    }
                  ],
                  "verificationCommands": ["mvn -Dtest=CalculatorTest test"]
                }
                """));
        RecordingVerifier verifier = new RecordingVerifier(true, "CalculatorTest passed");
        CodingAgentWorkflow workflow = new CodingAgentWorkflow(
                workspace,
                model,
                verifier,
                new ObjectMapper(),
                CodingAgentPolicy.defaults()
        );

        CodingAgentRunResult result = workflow.run(new CodingTask(
                "fix calculator",
                List.of("Calculator.java")
        ));

        assertEquals("return a + b;\n", Files.readString(workspace.resolve("Calculator.java")));
        assertEquals(List.of("mvn -Dtest=CalculatorTest test"), verifier.commands());
        assertTrue(result.success());
        assertTrue(result.trajectory().events().stream().anyMatch(event -> "apply_patch".equals(event.attributes().get("toolName"))));
        assertTrue(result.trajectory().events().stream().anyMatch(event -> "verify".equals(event.attributes().get("toolName"))));
    }

    @Test
    void rejectsPatchThatEscapesWorkspace() throws Exception {
        Files.writeString(workspace.resolve("Calculator.java"), "return a - b;\n", StandardCharsets.UTF_8);
        ModelProvider model = request -> ChatResponse.of(ModelTurn.finalAnswer("""
                {
                  "summary": "Write outside workspace",
                  "patches": [
                    {
                      "path": "../outside.txt",
                      "expected": "",
                      "replacement": "bad"
                    }
                  ],
                  "verificationCommands": ["mvn test"]
                }
                """));
        RecordingVerifier verifier = new RecordingVerifier(true, "should not run");
        CodingAgentWorkflow workflow = new CodingAgentWorkflow(
                workspace,
                model,
                verifier,
                new ObjectMapper(),
                CodingAgentPolicy.defaults()
        );

        CodingAgentRunResult result = workflow.run(new CodingTask(
                "try unsafe patch",
                List.of("Calculator.java")
        ));

        assertFalse(result.success());
        assertTrue(verifier.commands().isEmpty());
        assertEquals("return a - b;\n", Files.readString(workspace.resolve("Calculator.java")));
        assertTrue(result.trajectory().events().stream().anyMatch(event ->
                "patch path escapes workspace".equals(event.attributes().get("content"))));
    }

    @Test
    void rejectsPatchReachedThroughASymlinkOutsideWorkspace() throws Exception {
        Files.writeString(workspace.resolve("Calculator.java"), "unchanged\n", StandardCharsets.UTF_8);
        Path outside = Files.createTempDirectory(workspace.getParent(), "outside-");
        Files.writeString(outside.resolve("secret.txt"), "before", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(workspace.resolve("linked"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException error) {
            return;
        }
        ModelProvider model = request -> ChatResponse.of(ModelTurn.finalAnswer("""
                {
                  "summary": "Unsafe symlink patch",
                  "patches": [{
                    "path": "linked/secret.txt",
                    "expected": "before",
                    "replacement": "after"
                  }],
                  "verificationCommands": ["mvn test"]
                }
                """));
        CodingAgentWorkflow workflow = new CodingAgentWorkflow(
                workspace,
                model,
                new RecordingVerifier(true, "should not run"),
                new ObjectMapper(),
                CodingAgentPolicy.defaults()
        );

        CodingAgentRunResult result = workflow.run(new CodingTask(
                "try unsafe patch", List.of("Calculator.java")
        ));

        assertFalse(result.success());
        assertEquals("before", Files.readString(outside.resolve("secret.txt")));
    }

    @Test
    void rejectsVerificationCommandOutsideAllowList() throws Exception {
        Files.writeString(workspace.resolve("Calculator.java"), "return a - b;\n", StandardCharsets.UTF_8);
        ModelProvider model = request -> ChatResponse.of(ModelTurn.finalAnswer("""
                {
                  "summary": "Fix addition bug",
                  "patches": [
                    {
                      "path": "Calculator.java",
                      "expected": "return a - b;",
                      "replacement": "return a + b;"
                    }
                  ],
                  "verificationCommands": ["rm -rf target"]
                }
                """));
        RecordingVerifier verifier = new RecordingVerifier(true, "should not run");
        CodingAgentWorkflow workflow = new CodingAgentWorkflow(
                workspace,
                model,
                verifier,
                new ObjectMapper(),
                CodingAgentPolicy.defaults()
        );

        CodingAgentRunResult result = workflow.run(new CodingTask(
                "fix calculator with unsafe command",
                List.of("Calculator.java")
        ));

        assertFalse(result.success());
        assertTrue(verifier.commands().isEmpty());
        assertEquals("return a - b;\n", Files.readString(workspace.resolve("Calculator.java")));
        assertTrue(result.trajectory().events().stream().anyMatch(event ->
                "verification command is not allowed: rm -rf target".equals(event.attributes().get("content"))));
    }

    @Test
    void refusesAmbiguousPatchWithoutChangingFile() throws Exception {
        Files.writeString(workspace.resolve("values.txt"), "same\nsame\n", StandardCharsets.UTF_8);
        ModelProvider model = request -> ChatResponse.of(ModelTurn.finalAnswer("""
                {
                  "summary": "Change one value",
                  "patches": [
                    {
                      "path": "values.txt",
                      "expected": "same",
                      "replacement": "changed"
                    }
                  ],
                  "verificationCommands": ["mvn test"]
                }
                """));
        RecordingVerifier verifier = new RecordingVerifier(true, "should not run");
        CodingAgentWorkflow workflow = new CodingAgentWorkflow(
                workspace,
                model,
                verifier,
                new ObjectMapper(),
                CodingAgentPolicy.defaults()
        );

        CodingAgentRunResult result = workflow.run(new CodingTask("change one value", List.of("values.txt")));

        assertFalse(result.success());
        assertTrue(result.message().contains("2 matches"));
        assertEquals("same\nsame\n", Files.readString(workspace.resolve("values.txt")));
        assertTrue(verifier.commands().isEmpty());
    }

    private static final class RecordingVerifier implements VerificationRunner {
        private final boolean success;
        private final String output;
        private final List<String> commands = new java.util.ArrayList<>();

        private RecordingVerifier(boolean success, String output) {
            this.success = success;
            this.output = output;
        }

        @Override
        public VerificationResult run(String command, Path workspaceRoot) {
            commands.add(command);
            return new VerificationResult(command, success, output);
        }

        List<String> commands() {
            return List.copyOf(commands);
        }
    }
}
