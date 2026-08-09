package com.ading.ai.hermes.verification;

import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.terminal.LocalProcessTerminalBackend;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WorkspaceCompletionVerifier implements CompletionVerifier {

    private final JavaProjectVerificationDetector detector;
    private final ProjectVerificationRunner runner;
    private final Path workspace;

    public WorkspaceCompletionVerifier(Path workspace) {
        this(
                workspace,
                new JavaProjectVerificationDetector(),
                new ProjectVerificationRunner(
                        new LocalProcessTerminalBackend(workspace, Set.of("JAVA_HOME")),
                        Duration.ofMinutes(2)
                )
        );
    }

    WorkspaceCompletionVerifier(
            Path workspace,
            JavaProjectVerificationDetector detector,
            ProjectVerificationRunner runner
    ) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.detector = detector;
        this.runner = runner;
    }

    @Override
    public CompletionEvidence verify(AgentRunResult result) {
        if (!changedWorkspace(result)) {
            return CompletionEvidence.accept("read-only run requires no project verification");
        }
        Optional<ProjectVerificationRecipe> recipe = detector.detect(workspace);
        if (recipe.isEmpty()) {
            return CompletionEvidence.accept(
                    "workspace changed, but no supported Java build was detected"
            );
        }
        return runner.run(recipe.orElseThrow()).asCompletionEvidence();
    }

    private boolean changedWorkspace(AgentRunResult result) {
        Map<String, String> toolsByCallId = new HashMap<>();
        Set<String> successfulCalls = new HashSet<>();
        result.state().events().forEach(event -> {
            if (event.kind() == AgentEventKind.TOOL_REQUESTED) {
                toolsByCallId.put(event.toolRequest().callId(), event.toolRequest().name());
            } else if (event.kind() == AgentEventKind.TOOL_OBSERVED
                    && event.toolObservation().success()) {
                successfulCalls.add(event.toolObservation().callId());
            }
        });
        return successfulCalls.stream()
                .map(toolsByCallId::get)
                .anyMatch("edit_file"::equals);
    }
}
