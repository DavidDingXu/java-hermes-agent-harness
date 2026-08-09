package com.ading.ai.hermes.core;

@FunctionalInterface
public interface ModelDriver {

    ModelTurn next(AgentState state);
}
