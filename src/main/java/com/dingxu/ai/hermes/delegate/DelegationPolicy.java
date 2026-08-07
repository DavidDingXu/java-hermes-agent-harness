package com.dingxu.ai.hermes.delegate;

import com.dingxu.ai.hermes.core.IterationBudget;
import java.util.Objects;

public record DelegationPolicy(int maxConcurrentChildren, IterationBudget defaultBudget) {

    public DelegationPolicy {
        if (maxConcurrentChildren <= 0) {
            throw new IllegalArgumentException("maxConcurrentChildren must be positive");
        }
        Objects.requireNonNull(defaultBudget, "defaultBudget must not be null");
    }
}
