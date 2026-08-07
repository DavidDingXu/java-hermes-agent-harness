package com.dingxu.ai.hermes.checkpoint;

import java.time.Instant;
import java.util.List;

public record WorkspaceCheckpoint(String id, List<String> paths, Instant createdAt) {
    public WorkspaceCheckpoint {
        paths = List.copyOf(paths);
    }
}
