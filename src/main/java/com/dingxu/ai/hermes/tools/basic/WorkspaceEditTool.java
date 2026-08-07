package com.dingxu.ai.hermes.tools.basic;

import com.dingxu.ai.hermes.core.ToolRequest;
import com.dingxu.ai.hermes.tool.ToolDefinition;
import com.dingxu.ai.hermes.tool.ToolRegistry;
import com.dingxu.ai.hermes.tool.ToolResult;
import com.dingxu.ai.hermes.tool.ToolSchema;
import com.dingxu.ai.hermes.workspace.WorkspacePathPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class WorkspaceEditTool {

    private final WorkspacePathPolicy pathPolicy;

    public WorkspaceEditTool(Path workspaceRoot) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        this.pathPolicy = new WorkspacePathPolicy(workspaceRoot);
    }

    public ToolRegistry registerInto(ToolRegistry registry) {
        return registry.register(definition());
    }

    public ToolDefinition definition() {
        return new ToolDefinition(
                "edit_file",
                "Replace exactly one text match in a UTF-8 workspace file",
                ToolSchema.object()
                        .requiredString("path")
                        .requiredString("expected")
                        .requiredString("replacement"),
                this::edit
        );
    }

    private ToolResult edit(ToolRequest request) {
        String rawPath = String.valueOf(request.arguments().get("path"));
        Path target = resolveExistingFile(rawPath);
        if (target == null) {
            return ToolResult.failure(request.callId(), "path is outside workspace or is not a file: " + rawPath);
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            UniqueTextEditResult edit = UniqueTextEdit.apply(
                    content,
                    String.valueOf(request.arguments().get("expected")),
                    String.valueOf(request.arguments().get("replacement"))
            );
            if (!edit.success()) {
                return ToolResult.failure(request.callId(), edit.error());
            }
            Files.writeString(target, edit.content(), StandardCharsets.UTF_8);
            return ToolResult.success(request.callId(), "updated " + rawPath);
        } catch (IOException error) {
            return ToolResult.failure(request.callId(), "failed to edit file: " + rawPath);
        }
    }

    private Path resolveExistingFile(String rawPath) {
        try {
            Path resolved = pathPolicy.resolveExisting(rawPath);
            return Files.isRegularFile(resolved) ? resolved : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
