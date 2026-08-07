package com.dingxu.ai.hermes.memory;

import java.util.Objects;

public record MemoryWriteResult(boolean written, MemoryDecision decision) {

    public MemoryWriteResult {
        Objects.requireNonNull(decision, "decision must not be null");
    }
}
