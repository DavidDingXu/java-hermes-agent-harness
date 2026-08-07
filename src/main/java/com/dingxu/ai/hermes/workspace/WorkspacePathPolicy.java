package com.dingxu.ai.hermes.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class WorkspacePathPolicy {

    private final Path realRoot;

    public WorkspacePathPolicy(Path workspaceRoot) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        try {
            realRoot = workspaceRoot.toRealPath();
        } catch (IOException error) {
            throw new IllegalArgumentException("workspace root must exist", error);
        }
        if (!Files.isDirectory(realRoot)) {
            throw new IllegalArgumentException("workspace root must be a directory");
        }
    }

    public Path root() {
        return realRoot;
    }

    public Path resolveExisting(String relativePath) {
        Path candidate = lexicalCandidate(relativePath);
        try {
            return requireInside(candidate.toRealPath());
        } catch (IOException error) {
            throw new IllegalArgumentException("workspace path does not exist: " + relativePath, error);
        }
    }

    public Path resolveForWrite(String relativePath) {
        Path candidate = lexicalCandidate(relativePath);
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return resolveExisting(relativePath);
        }

        Deque<Path> missing = new ArrayDeque<>();
        Path ancestor = candidate;
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(ancestor.getFileName());
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            throw new IllegalArgumentException("workspace path has no existing parent: " + relativePath);
        }
        try {
            Path resolved = requireInside(ancestor.toRealPath());
            for (Path segment : missing) {
                resolved = resolved.resolve(segment);
            }
            return requireInside(resolved.normalize());
        } catch (IOException error) {
            throw new IllegalArgumentException("cannot resolve workspace path: " + relativePath, error);
        }
    }

    public boolean contains(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            Path resolved = Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    ? path.toRealPath()
                    : path.toAbsolutePath().normalize();
            return resolved.startsWith(realRoot);
        } catch (IOException error) {
            return false;
        }
    }

    private Path lexicalCandidate(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("workspace path must not be blank");
        }
        final Path requested;
        try {
            requested = Path.of(relativePath);
        } catch (InvalidPathException error) {
            throw new IllegalArgumentException("invalid workspace path: " + relativePath, error);
        }
        if (requested.isAbsolute()) {
            throw new IllegalArgumentException("absolute path is outside workspace: " + relativePath);
        }
        return requireInside(realRoot.resolve(requested).normalize());
    }

    private Path requireInside(Path path) {
        if (!path.startsWith(realRoot)) {
            throw new IllegalArgumentException("path is outside workspace: " + path);
        }
        return path;
    }
}
