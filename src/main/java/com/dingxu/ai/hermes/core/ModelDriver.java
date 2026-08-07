package com.dingxu.ai.hermes.core;

@FunctionalInterface
public interface ModelDriver {

    ModelTurn next(AgentState state);
}
