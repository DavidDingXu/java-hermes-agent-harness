package com.ading.ai.hermes.harness;

import com.ading.ai.hermes.checkpoint.WorkspaceCheckpoint;
import com.ading.ai.hermes.core.AgentRunResult;
import java.util.List;
import java.util.Optional;

public record HarnessRunResult(
        String runId,
        HarnessRunStatus status,
        String message,
        Optional<AgentRunResult> agentResult,
        Optional<WorkspaceCheckpoint> checkpoint,
        List<String> warnings
) {
    public HarnessRunResult {
        warnings = List.copyOf(warnings);
    }
}
