package com.ading.ai.hermes.context;

import com.ading.ai.hermes.core.AgentState;
import java.util.Objects;

public record ContextCompactionResult(AgentState state, boolean compacted, ContextCompactionReport report) {

    public ContextCompactionResult {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(report, "report must not be null");
    }
}
