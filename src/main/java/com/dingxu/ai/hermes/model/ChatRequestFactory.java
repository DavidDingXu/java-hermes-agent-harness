package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.AgentState;

@FunctionalInterface
public interface ChatRequestFactory {

    ChatRequest create(AgentState state);
}
