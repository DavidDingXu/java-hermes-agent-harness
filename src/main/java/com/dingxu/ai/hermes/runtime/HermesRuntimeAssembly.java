package com.dingxu.ai.hermes.runtime;

import com.dingxu.ai.hermes.core.AgentRuntime;
import com.dingxu.ai.hermes.gateway.local.LocalServiceRegistry;
import com.dingxu.ai.hermes.harness.AgentHarness;
import com.dingxu.ai.hermes.run.RunCoordinator;
import com.dingxu.ai.hermes.tool.ToolRegistry;
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
