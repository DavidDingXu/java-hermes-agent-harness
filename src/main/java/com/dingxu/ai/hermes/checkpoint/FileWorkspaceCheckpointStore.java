package com.dingxu.ai.hermes.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingxu.ai.hermes.workspace.WorkspacePathPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class FileWorkspaceCheckpointStore implements WorkspaceCheckpointStore {

    private static final String MANIFEST_FILE = "manifest.json";

    private final Path workspace;
    private final WorkspacePathPolicy pathPolicy;
    private final Path checkpointRoot;
    private final ObjectMapper objectMapper;

    public FileWorkspaceCheckpointStore(Path workspace) {
        this(workspace, workspace.resolve(".java-hermes/checkpoints"), new ObjectMapper());
    }

    public FileWorkspaceCheckpointStore(Path workspace, Path checkpointRoot, ObjectMapper objectMapper) {
        this.pathPolicy = new WorkspacePathPolicy(workspace);
        this.workspace = pathPolicy.root();
        this.checkpointRoot = checkpointRoot.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkspaceCheckpoint capture(List<String> relativePaths) throws IOException {
        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        Instant createdAt = Instant.now();
        Path checkpointDirectory = checkpointRoot.resolve(id);
        Path filesDirectory = checkpointDirectory.resolve("files");
        Files.createDirectories(filesDirectory);

        List<ManifestEntry> entries = new ArrayList<>();
        for (String relativePath : List.copyOf(relativePaths)) {
            Path source = resolveWorkspaceFile(relativePath);
            String portablePath = portable(workspace.relativize(source));
            boolean existed = Files.exists(source);
            if (existed && !Files.isRegularFile(source)) {
                throw new IllegalArgumentException("checkpoint path must be a file: " + relativePath);
            }
            entries.add(new ManifestEntry(portablePath, existed));
            if (existed) {
                Path snapshot = filesDirectory.resolve(portablePath);
                Files.createDirectories(snapshot.getParent());
                Files.copy(source, snapshot, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Manifest manifest = new Manifest(id, createdAt.toString(), entries);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(checkpointDirectory.resolve(MANIFEST_FILE).toFile(), manifest);
        return new WorkspaceCheckpoint(
                id,
                entries.stream().map(ManifestEntry::path).toList(),
                createdAt
        );
    }

    @Override
    public List<WorkspaceChange> diff(String checkpointId) throws IOException {
        Manifest manifest = readManifest(checkpointId);
        Path filesDirectory = checkpointRoot.resolve(checkpointId).resolve("files");
        List<WorkspaceChange> changes = new ArrayList<>();
        for (ManifestEntry entry : manifest.entries()) {
            Path current = resolveWorkspaceFile(entry.path());
            if (!entry.existed() && Files.exists(current)) {
                changes.add(new WorkspaceChange(entry.path(), WorkspaceChangeKind.CREATED));
                continue;
            }
            if (entry.existed() && !Files.exists(current)) {
                changes.add(new WorkspaceChange(entry.path(), WorkspaceChangeKind.DELETED));
                continue;
            }
            if (entry.existed()) {
                Path snapshot = filesDirectory.resolve(entry.path());
                if (!Arrays.equals(Files.readAllBytes(snapshot), Files.readAllBytes(current))) {
                    changes.add(new WorkspaceChange(entry.path(), WorkspaceChangeKind.MODIFIED));
                }
            }
        }
        return List.copyOf(changes);
    }

    @Override
    public WorkspaceRollbackResult rollback(String checkpointId) throws IOException {
        Manifest manifest = readManifest(checkpointId);
        Path filesDirectory = checkpointRoot.resolve(checkpointId).resolve("files");
        List<String> restored = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        for (ManifestEntry entry : manifest.entries()) {
            Path current = resolveWorkspaceFile(entry.path());
            if (entry.existed()) {
                Files.createDirectories(current.getParent());
                Files.copy(
                        filesDirectory.resolve(entry.path()),
                        current,
                        StandardCopyOption.REPLACE_EXISTING
                );
                restored.add(entry.path());
            } else if (Files.exists(current)) {
                if (!Files.isRegularFile(current)) {
                    throw new IOException("refusing to remove non-file during rollback: " + entry.path());
                }
                Files.delete(current);
                removed.add(entry.path());
            }
        }
        return new WorkspaceRollbackResult(restored, removed);
    }

    private Manifest readManifest(String checkpointId) throws IOException {
        if (checkpointId == null || !checkpointId.matches("[a-zA-Z0-9-]+")) {
            throw new IllegalArgumentException("invalid checkpoint id");
        }
        Path manifest = checkpointRoot.resolve(checkpointId).resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("checkpoint does not exist: " + checkpointId);
        }
        return objectMapper.readValue(manifest.toFile(), Manifest.class);
    }

    private Path resolveWorkspaceFile(String relativePath) {
        Path resolved = pathPolicy.resolveForWrite(relativePath);
        if (resolved.startsWith(checkpointRoot)
                || (Files.exists(checkpointRoot) && pathPolicy.contains(checkpointRoot)
                && resolved.startsWith(toRealPath(checkpointRoot)))) {
            throw new IllegalArgumentException("cannot checkpoint the checkpoint store itself");
        }
        return resolved;
    }

    private static Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException error) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record Manifest(String id, String createdAt, List<ManifestEntry> entries) {
    }

    private record ManifestEntry(String path, boolean existed) {
    }
}
