package com.ading.ai.hermes.run;

import java.util.Optional;

public record RunSnapshot(
        String runId,
        String sessionId,
        RunStatus status,
        String input,
        String output,
        boolean stopRequested,
        int queuedInputs,
        Optional<String> pendingSteer,
        Optional<RunApproval> pendingApproval,
        long eventCount
) {
}
