package com.ading.ai.hermes.context;

import com.ading.ai.hermes.core.AgentRunResult;

@FunctionalInterface
public interface ContextTurnObserver {

    void onCompleted(AgentRunResult result);

    static ContextTurnObserver noop() {
        return result -> { };
    }
}
