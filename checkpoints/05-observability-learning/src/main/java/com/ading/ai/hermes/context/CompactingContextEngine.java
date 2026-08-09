package com.ading.ai.hermes.context;

import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import java.util.Objects;

public final class CompactingContextEngine implements ContextEngine {

    private final ContextCompactor compactor;
    private final ContextTurnObserver observer;

    public CompactingContextEngine(ContextCompactor compactor) {
        this(compactor, ContextTurnObserver.noop());
    }

    public CompactingContextEngine(ContextCompactor compactor, ContextTurnObserver observer) {
        this.compactor = Objects.requireNonNull(compactor, "compactor must not be null");
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
    }

    @Override
    public ContextCompactionResult select(AgentState state) {
        return compactor.compact(state);
    }

    @Override
    public void onTurnCompleted(AgentRunResult result) {
        observer.onCompleted(Objects.requireNonNull(result, "result must not be null"));
    }
}
