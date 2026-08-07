package com.dingxu.ai.hermes.checkpoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileWorkspaceCheckpointStoreTest {

    @TempDir
    Path workspace;

    @Test
    void previewsAndRollsBackModifiedAndCreatedFiles() throws Exception {
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.java"), "before\n");
        FileWorkspaceCheckpointStore store = new FileWorkspaceCheckpointStore(workspace);

        WorkspaceCheckpoint checkpoint = store.capture(List.of("src/App.java", "src/New.java"));
        Files.writeString(workspace.resolve("src/App.java"), "after\n");
        Files.writeString(workspace.resolve("src/New.java"), "created\n");

        List<WorkspaceChange> changes = store.diff(checkpoint.id());
        WorkspaceRollbackResult rollback = store.rollback(checkpoint.id());

        assertEquals(List.of(WorkspaceChangeKind.MODIFIED, WorkspaceChangeKind.CREATED),
                changes.stream().map(WorkspaceChange::kind).toList());
        assertEquals("before\n", Files.readString(workspace.resolve("src/App.java")));
        assertFalse(Files.exists(workspace.resolve("src/New.java")));
        assertEquals(List.of("src/App.java"), rollback.restoredPaths());
        assertEquals(List.of("src/New.java"), rollback.removedPaths());
    }

    @Test
    void persistsManifestSoAnotherStoreInstanceCanRollback() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "v1");
        WorkspaceCheckpoint checkpoint = new FileWorkspaceCheckpointStore(workspace)
                .capture(List.of("README.md"));
        Files.writeString(workspace.resolve("README.md"), "v2");

        new FileWorkspaceCheckpointStore(workspace).rollback(checkpoint.id());

        assertEquals("v1", Files.readString(workspace.resolve("README.md")));
    }

    @Test
    void rejectsCheckpointPathsOutsideWorkspace() {
        FileWorkspaceCheckpointStore store = new FileWorkspaceCheckpointStore(workspace);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> store.capture(List.of("../secret.txt"))
        );

        assertTrue(error.getMessage().contains("outside workspace"));
    }

    @Test
    void rejectsCheckpointFilesReachedThroughASymlink() throws Exception {
        Path outside = Files.createTempDirectory(workspace.getParent(), "outside-");
        Files.writeString(outside.resolve("secret.txt"), "before");
        try {
            Files.createSymbolicLink(workspace.resolve("linked"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException error) {
            return;
        }
        FileWorkspaceCheckpointStore store = new FileWorkspaceCheckpointStore(workspace);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> store.capture(List.of("linked/secret.txt"))
        );

        assertTrue(error.getMessage().contains("outside workspace"));
        assertEquals("before", Files.readString(outside.resolve("secret.txt")));
    }
}
