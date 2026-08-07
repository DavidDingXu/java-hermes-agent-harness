package com.dingxu.ai.hermes.context.reference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextReferenceResolverTest {

    @TempDir
    Path workspace;

    @Test
    void expandsWorkspaceGitAndUrlReferencesInInputOrder() throws Exception {
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.java"), "line-1\nline-2\nline-3\n");
        Files.writeString(workspace.resolve("src/Helper.java"), "helper\n");

        ContextReferenceResolver resolver = new ContextReferenceResolver(
                workspace,
                10_000,
                url -> "page:" + url,
                reference -> Map.of("diff", "diff-content", "git:2", "commit-content").get(reference)
        );

        ContextReferenceResult result = resolver.resolve(
                "review @file:src/App.java:2-3 @folder:src @diff @git:2 @url:https://example.com/spec"
        );

        assertFalse(result.blocked());
        assertEquals(5, result.references().size());
        assertTrue(result.attachedContext().contains("line-2\nline-3"));
        assertTrue(result.attachedContext().contains("src/Helper.java"));
        assertTrue(result.attachedContext().contains("diff-content"));
        assertTrue(result.attachedContext().contains("commit-content"));
        assertTrue(result.attachedContext().contains("page:https://example.com/spec"));
    }

    @Test
    void blocksFilesOutsideWorkspace() throws Exception {
        Path secret = workspace.getParent().resolve("secret.txt");
        Files.writeString(secret, "secret");
        ContextReferenceResolver resolver = new ContextReferenceResolver(
                workspace, 10_000, url -> "", reference -> ""
        );

        ContextReferenceResult result = resolver.resolve("inspect @file:../secret.txt");

        assertFalse(result.blocked());
        assertTrue(result.attachedContext().isEmpty());
        assertTrue(result.warnings().getFirst().contains("outside workspace"));
    }

    @Test
    void blocksFilesReachedThroughASymlinkOutsideWorkspace() throws Exception {
        Path outside = Files.createTempDirectory(workspace.getParent(), "outside-");
        Files.writeString(outside.resolve("secret.txt"), "secret");
        try {
            Files.createSymbolicLink(workspace.resolve("linked"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException error) {
            return;
        }
        ContextReferenceResolver resolver = new ContextReferenceResolver(
                workspace, 10_000, url -> "", reference -> ""
        );

        ContextReferenceResult result = resolver.resolve("inspect @file:linked/secret.txt");

        assertTrue(result.attachedContext().isEmpty());
        assertTrue(result.warnings().getFirst().contains("outside workspace"));
    }

    @Test
    void refusesTheWholeInjectionWhenHardLimitIsExceeded() throws Exception {
        Files.writeString(workspace.resolve("large.txt"), "x".repeat(200));
        ContextReferenceResolver resolver = new ContextReferenceResolver(
                workspace, 100, url -> "", reference -> ""
        );

        ContextReferenceResult result = resolver.resolve("inspect @file:large.txt");

        assertTrue(result.blocked());
        assertTrue(result.attachedContext().isEmpty());
        assertTrue(result.warnings().getFirst().contains("hard limit"));
    }
}
