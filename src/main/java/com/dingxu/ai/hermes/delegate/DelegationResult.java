package com.dingxu.ai.hermes.delegate;

import java.util.List;

public record DelegationResult(List<SubAgentResult> results) {

    public DelegationResult {
        results = List.copyOf(results);
    }
}
