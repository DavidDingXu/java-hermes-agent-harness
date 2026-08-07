package com.dingxu.ai.hermes.context;

import com.dingxu.ai.hermes.core.AgentRunResult;

@FunctionalInterface
public interface ContextTurnObserver {

    void onCompleted(AgentRunResult result);

    static ContextTurnObserver noop() {
        return result -> { };
    }
}
