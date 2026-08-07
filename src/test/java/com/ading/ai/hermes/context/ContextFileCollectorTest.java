package com.ading.ai.hermes.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextFileCollectorTest {

    @TempDir
    Path workspace;

    @Test
    void collectsExplicitFilesInOrder() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "readme");
        Files.writeString(workspace.resolve("AGENT.md"), "agent rules");
        ContextFileCollector collector = new ContextFileCollector(workspace, 100);

        ContextFileSet files = collector.collect(List.of("README.md", "AGENT.md"));

        assertEquals(List.of(
                new ContextFile("README.md", "readme"),
                new ContextFile("AGENT.md", "agent rules")
        ), files.files());
        assertEquals(List.of(), files.rejections());
    }

    @Test
    void rejectsAbsolutePath() {
        ContextFileCollector collector = new ContextFileCollector(workspace, 100);

        ContextFileSet files = collector.collect(List.of(workspace.resolve("README.md").toString()));

        assertEquals(List.of(), files.files());
        assertEquals(List.of(new ContextFileRejection(
                workspace.resolve("README.md").toString(),
                "absolute path is not allowed"
        )), files.rejections());
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        Path secret = workspace.getParent().resolve("secret.md");
        Files.writeString(secret, "secret");
        try {
            ContextFileCollector collector = new ContextFileCollector(workspace, 100);

            ContextFileSet files = collector.collect(List.of("../secret.md"));

            assertEquals(List.of(), files.files());
            assertEquals(List.of(new ContextFileRejection(
                    "../secret.md",
                    "path escapes workspace"
            )), files.rejections());
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    @Test
    void rejectsDirectory() throws Exception {
        Files.createDirectories(workspace.resolve("docs"));
        ContextFileCollector collector = new ContextFileCollector(workspace, 100);

        ContextFileSet files = collector.collect(List.of("docs"));

        assertEquals(List.of(), files.files());
        assertEquals(List.of(new ContextFileRejection("docs", "path is not a file")), files.rejections());
    }

    @Test
    void rejectsLargeFile() throws Exception {
        Files.writeString(workspace.resolve("large.md"), "123456");
        ContextFileCollector collector = new ContextFileCollector(workspace, 5);

        ContextFileSet files = collector.collect(List.of("large.md"));

        assertEquals(List.of(), files.files());
        assertEquals(List.of(new ContextFileRejection(
                "large.md",
                "context file too large: 6 chars, limit 5"
        )), files.rejections());
    }
}
