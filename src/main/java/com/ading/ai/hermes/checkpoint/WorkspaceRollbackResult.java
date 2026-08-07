package com.ading.ai.hermes.checkpoint;

import java.util.List;

public record WorkspaceRollbackResult(List<String> restoredPaths, List<String> removedPaths) {
    public WorkspaceRollbackResult {
        restoredPaths = List.copyOf(restoredPaths);
        removedPaths = List.copyOf(removedPaths);
    }
}
