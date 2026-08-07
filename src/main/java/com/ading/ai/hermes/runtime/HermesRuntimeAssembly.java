package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.gateway.local.LocalServiceRegistry;
import com.ading.ai.hermes.harness.AgentHarness;
import com.ading.ai.hermes.run.RunCoordinator;
import com.ading.ai.hermes.tool.ToolRegistry;
import java.util.Objects;

public record HermesRuntimeAssembly(
        AgentRuntime runtime,
        AgentHarness harness,
        RunCoordinator runs,
        ToolRegistry tools,
        LocalServiceRegistry localServices
) {
    public HermesRuntimeAssembly {
        Objects.requireNonNull(runtime, "runtime must not be null");
        Objects.requireNonNull(harness, "harness must not be null");
        Objects.requireNonNull(runs, "runs must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(localServices, "localServices must not be null");
    }
}
