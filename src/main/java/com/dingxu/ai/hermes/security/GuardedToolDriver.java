package com.dingxu.ai.hermes.security;

import com.dingxu.ai.hermes.core.ToolDriver;
import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Objects;

public final class GuardedToolDriver implements ToolDriver {

    private final ToolDriver delegate;
    private final List<ToolPolicy> policies;

    public GuardedToolDriver(ToolDriver delegate, List<ToolPolicy> policies) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.policies = List.copyOf(policies);
    }

    @Override
    public ToolObservation execute(ToolRequest request) {
        for (ToolPolicy policy : policies) {
            ToolDecision decision = policy.decide(request);
            if (!decision.allowed()) {
                return ToolObservation.failure(request.callId(), "tool request blocked: " + decision.reason());
            }
        }
        return delegate.execute(request);
    }
}
