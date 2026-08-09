package com.ading.ai.hermes.verification;

import com.ading.ai.hermes.terminal.TerminalStatus;
import java.util.List;
import java.util.Objects;

public record ProjectVerificationResult(List<ProjectVerificationStep> steps) {

    public ProjectVerificationResult {
        steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
    }

    public boolean passed() {
        return !steps.isEmpty() && steps.stream()
                .allMatch(step -> step.status() == TerminalStatus.SUCCESS);
    }

    public CompletionEvidence asCompletionEvidence() {
        if (passed()) {
            return CompletionEvidence.accept("all project verification commands passed");
        }
        ProjectVerificationStep failed = steps.stream()
                .filter(step -> step.status() != TerminalStatus.SUCCESS)
                .findFirst()
                .orElse(null);
        if (failed == null) {
            return CompletionEvidence.reject("no project verification command was executed");
        }
        return CompletionEvidence.reject(
                "project verification phase '" + failed.phase() + "' failed with exit code "
                        + failed.exitCode()
        );
    }
}
