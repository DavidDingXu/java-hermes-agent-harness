package com.ading.ai.hermes.harness;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.StopSignal;

@FunctionalInterface
public interface HarnessRuntime {

    AgentRunResult run(AgentRunRequest request, StopSignal stopSignal);
}
