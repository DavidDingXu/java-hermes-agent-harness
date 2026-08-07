package com.dingxu.ai.hermes.core;

@FunctionalInterface
public interface AgentRuntime {

    AgentRunResult run(AgentRunRequest request);
}
