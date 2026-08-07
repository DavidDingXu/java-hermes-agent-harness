package com.dingxu.ai.hermes.harness;

import com.dingxu.ai.hermes.core.AgentRunRequest;
import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.StopSignal;

@FunctionalInterface
public interface HarnessRuntime {

    AgentRunResult run(AgentRunRequest request, StopSignal stopSignal);
}
