package com.ading.ai.hermes.examples.coding;

import com.ading.ai.hermes.observability.TrajectoryRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CodingAgentRunResult(
        boolean success,
        String message,
        Optional<CodingPlan> plan,
        List<VerificationResult> verificationResults,
        TrajectoryRecord trajectory
) {

    public CodingAgentRunResult {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(verificationResults, "verificationResults must not be null");
        Objects.requireNonNull(trajectory, "trajectory must not be null");
        message = message.trim();
        verificationResults = List.copyOf(verificationResults);
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
