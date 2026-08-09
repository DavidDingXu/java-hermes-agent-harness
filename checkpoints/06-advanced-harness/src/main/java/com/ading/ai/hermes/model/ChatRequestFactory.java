package com.ading.ai.hermes.model;

import com.ading.ai.hermes.core.AgentState;

@FunctionalInterface
public interface ChatRequestFactory {

    ChatRequest create(AgentState state);
}
