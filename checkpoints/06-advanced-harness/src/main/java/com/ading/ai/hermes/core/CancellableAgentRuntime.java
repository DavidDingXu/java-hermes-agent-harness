package com.ading.ai.hermes.core;

import java.util.Objects;

@FunctionalInterface
public interface CancellableAgentRuntime extends AgentRuntime {

    AgentRunResult run(AgentRunRequest request, StopSignal stopSignal);

    @Override
    default AgentRunResult run(AgentRunRequest request) {
        return run(request, StopSignal.none());
    }

    static CancellableAgentRuntime adapt(AgentRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime must not be null");
        if (runtime instanceof CancellableAgentRuntime cancellable) {
            return cancellable;
        }
        return (request, stopSignal) -> runtime.run(request);
    }
}
