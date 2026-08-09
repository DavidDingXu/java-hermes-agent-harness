package com.ading.ai.hermes.workspace;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class SensitiveWorkspacePathPolicy {

    private final Path workspaceRoot;

    public SensitiveWorkspacePathPolicy(Path workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null")
                .toAbsolutePath()
                .normalize();
    }

    public boolean isProtected(Path path) {
        Path normalized = Objects.requireNonNull(path, "path must not be null")
                .toAbsolutePath()
                .normalize();
        if (!normalized.startsWith(workspaceRoot)) {
            return true;
        }
        Path relative = workspaceRoot.relativize(normalized);
        for (Path segment : relative) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (name.equals(".git") || name.equals(".hermes")) {
                return true;
            }
        }
        Path fileName = relative.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        return name.equals("hermes.local.properties")
                || name.equals(".env")
                || name.startsWith(".env.");
    }
}
