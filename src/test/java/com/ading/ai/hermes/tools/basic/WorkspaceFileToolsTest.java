package com.ading.ai.hermes.tools.basic;

import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.tool.ToolRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceFileToolsTest {

    @TempDir
    Path workspace;

    @Test
    void readsUtf8FileInsideWorkspace() throws Exception {
        Files.createDirectories(workspace.resolve("docs"));
        Files.writeString(workspace.resolve("docs/notes.md"), "hello hermes");
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "docs/notes.md"))
        );

        assertTrue(observation.success());
        assertEquals("hello hermes", observation.content());
    }

    @Test
    void listsDirectoryEntriesInStableOrder() throws Exception {
        Files.writeString(workspace.resolve("b.txt"), "b");
        Files.writeString(workspace.resolve("a.txt"), "a");
        Files.createDirectories(workspace.resolve("docs"));
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "list_directory", Map.of("path", "."))
        );

        assertTrue(observation.success());
        assertEquals("a.txt\nb.txt\ndocs/", observation.content());
    }

    @Test
    void rejectsPathTraversalBeforeReading() throws Exception {
        Path secret = workspace.getParent().resolve("secret.txt");
        Files.writeString(secret, "secret");
        try {
            ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

            ToolObservation observation = registry.execute(
                    new ToolRequest("call-1", "read_file", Map.of("path", "../secret.txt"))
            );

            assertEquals(false, observation.success());
            assertEquals("path escapes workspace: ../secret.txt", observation.content());
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    @Test
    void rejectsAbsolutePath() {
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", workspace.resolve("notes.md").toString()))
        );

        assertEquals(false, observation.success());
        assertEquals("absolute path is not allowed: " + workspace.resolve("notes.md"), observation.content());
    }

    @Test
    void rejectsFileReachedThroughSymlinkOutsideWorkspace() throws Exception {
        Path outside = Files.createTempDirectory(workspace.getParent(), "outside-");
        Files.writeString(outside.resolve("secret.txt"), "secret");
        try {
            Files.createSymbolicLink(workspace.resolve("linked"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException error) {
            return;
        }
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "linked/secret.txt"))
        );

        assertEquals(false, observation.success());
        assertTrue(observation.content().contains("path escapes workspace"));
    }

    @Test
    void rejectsReadingDirectoryAsFile() throws Exception {
        Files.createDirectories(workspace.resolve("docs"));
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "docs"))
        );

        assertEquals(false, observation.success());
        assertEquals("path is not a file: docs", observation.content());
    }

    @Test
    void rejectsLargeFileBeforeReturningContent() throws Exception {
        Files.writeString(workspace.resolve("large.txt"), "123456");
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 5));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "large.txt"))
        );

        assertEquals(false, observation.success());
        assertEquals("file too large: large.txt (limit 5 chars)", observation.content());
    }

    @Test
    void rejectsInvalidPathSyntaxAsAToolFailure() {
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "bad\0path"))
        );

        assertEquals(false, observation.success());
        assertEquals("invalid workspace path", observation.content());
    }

    @Test
    void refusesDirectoryListingsBeyondTheEntryLimit() throws Exception {
        for (int index = 0; index < 201; index++) {
            Files.writeString(workspace.resolve("file-" + index + ".txt"), "x");
        }
        ToolRegistry registry = registry(new WorkspaceFileTools(workspace, 1000));

        ToolObservation observation = registry.execute(
                new ToolRequest("call-1", "list_directory", Map.of("path", "."))
        );

        assertEquals(false, observation.success());
        assertEquals("directory has more than 200 entries: .", observation.content());
    }

    private ToolRegistry registry(WorkspaceFileTools tools) {
        return tools.registerInto(ToolRegistry.empty());
    }
}
