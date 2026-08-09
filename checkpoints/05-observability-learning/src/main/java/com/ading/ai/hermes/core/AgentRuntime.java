package com.ading.ai.hermes.core;

@FunctionalInterface
public interface AgentRuntime {

    AgentRunResult run(AgentRunRequest request);
}
