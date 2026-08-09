package com.ading.ai.hermes.context;

import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;

public interface ContextEngine {

    ContextCompactionResult select(AgentState state);

    void onTurnCompleted(AgentRunResult result);
}
