package com.ading.ai.hermes.verification;

import com.ading.ai.hermes.terminal.TerminalBackend;
import com.ading.ai.hermes.terminal.TerminalCommand;
import com.ading.ai.hermes.terminal.TerminalResult;
import com.ading.ai.hermes.terminal.TerminalStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProjectVerificationRunner {

    private static final int MAX_OUTPUT_CHARACTERS = 50_000;

    private final TerminalBackend terminal;
    private final Duration commandTimeout;

    public ProjectVerificationRunner(TerminalBackend terminal, Duration commandTimeout) {
        this.terminal = Objects.requireNonNull(terminal, "terminal must not be null");
        if (commandTimeout == null || commandTimeout.isZero() || commandTimeout.isNegative()) {
            throw new IllegalArgumentException("commandTimeout must be positive");
        }
        this.commandTimeout = commandTimeout;
    }

    public ProjectVerificationResult run(ProjectVerificationRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe must not be null");
        List<ProjectVerificationStep> steps = new ArrayList<>();
        for (ProjectVerificationCommand command : recipe.commands()) {
            TerminalResult result = Objects.requireNonNull(
                    terminal.execute(new TerminalCommand(
                            command.argv(),
                            recipe.projectRoot(),
                            Map.of(),
                            commandTimeout,
                            MAX_OUTPUT_CHARACTERS
                    )),
                    "terminal result must not be null"
            );
            steps.add(new ProjectVerificationStep(
                    command.phase(),
                    result.status(),
                    result.exitCode(),
                    result.output(),
                    result.truncated()
            ));
            if (result.status() != TerminalStatus.SUCCESS) {
                break;
            }
        }
        return new ProjectVerificationResult(steps);
    }
}
