package com.dingxu.ai.hermes.runtime;

import com.dingxu.ai.hermes.core.AgentRunRequest;
import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentRuntime;
import com.dingxu.ai.hermes.harness.AgentHarness;
import com.dingxu.ai.hermes.harness.HarnessRunRequest;
import com.dingxu.ai.hermes.harness.HarnessRunResult;
import com.dingxu.ai.hermes.harness.HarnessRunStatus;
import java.util.List;
import java.util.Objects;

public final class HarnessAgentRuntime implements AgentRuntime {

    private final AgentHarness harness;

    public HarnessAgentRuntime(AgentHarness harness) {
        this.harness = Objects.requireNonNull(harness, "harness must not be null");
    }

    @Override
    public AgentRunResult run(AgentRunRequest request) {
        HarnessRunResult result = harness.run(new HarnessRunRequest(request, List.of()));
        if (result.status() == HarnessRunStatus.COMPLETED && result.agentResult().isPresent()) {
            return result.agentResult().orElseThrow();
        }
        throw new IllegalStateException(
                "harness run " + result.status().name().toLowerCase() + ": " + result.message()
        );
    }
}
