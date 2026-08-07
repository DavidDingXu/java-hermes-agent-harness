package com.ading.ai.hermes.tools.basic;

import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.tool.ToolDefinition;
import com.ading.ai.hermes.tool.ToolRegistry;
import com.ading.ai.hermes.tool.ToolResult;
import com.ading.ai.hermes.tool.ToolSchema;
import com.ading.ai.hermes.workspace.WorkspacePathPolicy;
import java.io.IOException;
import java.io.Reader;
import java.io.BufferedReader;
import java.nio.file.DirectoryStream;
import java.nio.file.InvalidPathException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class WorkspaceFileTools {

    private static final int MAX_DIRECTORY_ENTRIES = 200;

    private final Path workspaceRoot;
    private final WorkspacePathPolicy pathPolicy;
    private final int maxReadChars;

    public WorkspaceFileTools(Path workspaceRoot, int maxReadChars) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        if (maxReadChars <= 0) {
            throw new IllegalArgumentException("maxReadChars must be positive");
        }
        this.pathPolicy = new WorkspacePathPolicy(workspaceRoot);
        this.workspaceRoot = pathPolicy.root();
        this.maxReadChars = maxReadChars;
    }

    public ToolRegistry registerInto(ToolRegistry registry) {
        return registry
                .register(readFileDefinition())
                .register(listDirectoryDefinition());
    }

    public List<ToolDefinition> definitions() {
        return List.of(readFileDefinition(), listDirectoryDefinition());
    }

    public ToolDefinition readFileDefinition() {
        return new ToolDefinition(
                "read_file",
                "Read a UTF-8 file inside the workspace",
                ToolSchema.object().requiredString("path"),
                this::readFile
        );
    }

    public ToolDefinition listDirectoryDefinition() {
        return new ToolDefinition(
                "list_directory",
                "List entries in a workspace directory",
                ToolSchema.object().requiredString("path"),
                this::listDirectory
        );
    }

    private ToolResult readFile(ToolRequest request) {
        String rawPath = request.arguments().get("path").toString();
        Path resolved = resolvePath(rawPath);
        if (resolved == null) {
            return ToolResult.failure(request.callId(), pathError(rawPath));
        }
        if (!Files.exists(resolved)) {
            return ToolResult.failure(request.callId(), "file not found: " + rawPath);
        }
        Path realPath = toRealPath(resolved);
        if (realPath == null) {
            return ToolResult.failure(request.callId(), pathError(rawPath));
        }
        if (!Files.isRegularFile(realPath)) {
            return ToolResult.failure(request.callId(), "path is not a file: " + rawPath);
        }

        try {
            String content = readBounded(realPath);
            return ToolResult.success(request.callId(), content);
        } catch (ContentLimitExceeded error) {
            return ToolResult.failure(
                    request.callId(),
                    "file too large: " + rawPath + " (limit " + maxReadChars + " chars)"
            );
        } catch (IOException error) {
            return ToolResult.failure(request.callId(), "failed to read file: " + rawPath);
        }
    }

    private ToolResult listDirectory(ToolRequest request) {
        String rawPath = request.arguments().get("path").toString();
        Path resolved = resolvePath(rawPath);
        if (resolved == null) {
            return ToolResult.failure(request.callId(), pathError(rawPath));
        }
        if (!Files.exists(resolved)) {
            return ToolResult.failure(request.callId(), "directory not found: " + rawPath);
        }
        Path realPath = toRealPath(resolved);
        if (realPath == null) {
            return ToolResult.failure(request.callId(), pathError(rawPath));
        }
        if (!Files.isDirectory(realPath)) {
            return ToolResult.failure(request.callId(), "path is not a directory: " + rawPath);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(realPath)) {
            List<String> entries = new java.util.ArrayList<>();
            for (Path path : stream) {
                if (entries.size() == MAX_DIRECTORY_ENTRIES) {
                    return ToolResult.failure(
                            request.callId(),
                            "directory has more than " + MAX_DIRECTORY_ENTRIES + " entries: " + rawPath
                    );
                }
                entries.add(formatDirectoryEntry(path));
            }
            entries.sort(Comparator.naturalOrder());
            String content = String.join("\n", entries);
            if (content.length() > maxReadChars) {
                return ToolResult.failure(
                        request.callId(),
                        "directory listing exceeds limit " + maxReadChars + " chars: " + rawPath
                );
            }
            return ToolResult.success(request.callId(), content);
        } catch (IOException error) {
            return ToolResult.failure(request.callId(), "failed to list directory: " + rawPath);
        }
    }

    private Path resolvePath(String rawPath) {
        try {
            return pathPolicy.resolveForWrite(rawPath);
        } catch (IllegalArgumentException error) {
            return null;
        }
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
        try {
            if (Path.of(rawPath).isAbsolute()) {
                return "absolute path is not allowed: " + rawPath;
            }
        } catch (InvalidPathException error) {
            return "invalid workspace path";
        }
        return "path escapes workspace: " + rawPath;
    }

    private String readBounded(Path path) throws IOException, ContentLimitExceeded {
        StringBuilder content = new StringBuilder(Math.min(maxReadChars, 8192));
        try (Reader reader = new BufferedReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            char[] buffer = new char[Math.min(4096, maxReadChars)];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (content.length() + read > maxReadChars) {
                    throw new ContentLimitExceeded();
                }
                content.append(buffer, 0, read);
            }
        }
        return content.toString();
    }

    private String formatDirectoryEntry(Path path) {
        String name = path.getFileName().toString();
        if (Files.isDirectory(path)) {
            return name + "/";
        }
        return name;
    }

    private static final class ContentLimitExceeded extends Exception {
    }

}
