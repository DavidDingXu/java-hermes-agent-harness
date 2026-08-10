package com.ading.ai.hermes.core;

import java.util.Objects;

public final class InterruptibleAgentLoop {

    private final ErrorRecoveringAgentLoop delegate;

    public InterruptibleAgentLoop(ModelDriver modelDriver, ToolDriver toolDriver, StopSignal stopSignal) {
        this(modelDriver, toolDriver, stopSignal, ErrorRecoveryPolicy.maxRecoveries(0));
    }

    public InterruptibleAgentLoop(
            ModelDriver modelDriver,
            ToolDriver toolDriver,
            StopSignal stopSignal,
            ErrorRecoveryPolicy recoveryPolicy
    ) {
        delegate = new ErrorRecoveringAgentLoop(
                Objects.requireNonNull(modelDriver, "modelDriver must not be null"),
                Objects.requireNonNull(toolDriver, "toolDriver must not be null"),
                Objects.requireNonNull(stopSignal, "stopSignal must not be null"),
                Objects.requireNonNull(recoveryPolicy, "recoveryPolicy must not be null")
        );
    }

    public AgentRunResult run(AgentRunRequest request) {
        return delegate.run(request);
    }

    public AgentRunResult run(AgentRunRequest request, AgentState history) {
        return delegate.run(request, history);
    }
}
