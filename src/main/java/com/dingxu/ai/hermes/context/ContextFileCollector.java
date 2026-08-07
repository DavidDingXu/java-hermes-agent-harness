package com.dingxu.ai.hermes.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ContextFileCollector {

    private final Path workspaceRoot;
    private final int maxCharsPerFile;

    public ContextFileCollector(Path workspaceRoot, int maxCharsPerFile) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        if (maxCharsPerFile <= 0) {
            throw new IllegalArgumentException("maxCharsPerFile must be positive");
        }
        this.workspaceRoot = toRealDirectory(workspaceRoot);
        this.maxCharsPerFile = maxCharsPerFile;
    }

    public ContextFileSet collect(List<String> paths) {
        List<ContextFile> files = new ArrayList<>();
        List<ContextFileRejection> rejections = new ArrayList<>();

        for (String path : paths) {
            collectOne(path, files, rejections);
        }

        return new ContextFileSet(files, rejections);
    }

    private void collectOne(String rawPath, List<ContextFile> files, List<ContextFileRejection> rejections) {
        Path resolved = resolvePath(rawPath);
        if (resolved == null) {
            rejections.add(new ContextFileRejection(rawPath, pathError(rawPath)));
            return;
        }
        if (!Files.exists(resolved)) {
            rejections.add(new ContextFileRejection(rawPath, "file not found"));
            return;
        }

        Path realPath = toRealPath(resolved);
        if (realPath == null) {
            rejections.add(new ContextFileRejection(rawPath, "path escapes workspace"));
            return;
        }
        if (!Files.isRegularFile(realPath)) {
            rejections.add(new ContextFileRejection(rawPath, "path is not a file"));
            return;
        }

        try {
            String content = Files.readString(realPath, StandardCharsets.UTF_8);
            if (content.length() > maxCharsPerFile) {
                rejections.add(new ContextFileRejection(
                        rawPath,
                        "context file too large: " + content.length() + " chars, limit " + maxCharsPerFile
                ));
                return;
            }
            files.add(new ContextFile(rawPath, content));
        } catch (IOException error) {
            rejections.add(new ContextFileRejection(rawPath, "failed to read file"));
        }
    }

    private Path resolvePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path requested = Path.of(rawPath);
        if (requested.isAbsolute()) {
            return null;
        }
        Path resolved = workspaceRoot.resolve(requested).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            return null;
        }
        return resolved;
    }

    private Path toRealPath(Path resolved) {
        try {
            Path realPath = resolved.toRealPath();
            if (!realPath.startsWith(workspaceRoot)) {
                return null;
            }
            return realPath;
        } catch (IOException error) {
            return null;
        }
    }

    private String pathError(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "path must not be blank";
        }
        if (Path.of(rawPath).isAbsolute()) {
            return "absolute path is not allowed";
        }
        return "path escapes workspace";
    }

    private static Path toRealDirectory(Path workspaceRoot) {
        try {
            Path realRoot = workspaceRoot.toRealPath();
            if (!Files.isDirectory(realRoot)) {
                throw new IllegalArgumentException("workspaceRoot must be a directory");
            }
            return realRoot;
        } catch (IOException error) {
            throw new IllegalArgumentException("workspaceRoot must exist", error);
        }
    }
}
