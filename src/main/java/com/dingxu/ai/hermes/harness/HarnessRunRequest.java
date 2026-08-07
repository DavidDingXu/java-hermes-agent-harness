package com.dingxu.ai.hermes.harness;

import com.dingxu.ai.hermes.core.AgentRunRequest;
import java.util.List;

public record HarnessRunRequest(AgentRunRequest agentRequest, List<String> checkpointPaths) {
    public HarnessRunRequest {
        if (agentRequest == null) {
            throw new IllegalArgumentException("agentRequest must not be null");
        }
        checkpointPaths = List.copyOf(checkpointPaths);
    }
}
