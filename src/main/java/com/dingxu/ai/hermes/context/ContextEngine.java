package com.dingxu.ai.hermes.context;

import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentState;

public interface ContextEngine {

    ContextCompactionResult select(AgentState state);

    void onTurnCompleted(AgentRunResult result);
}
