package com.dingxu.ai.hermes.delegate;

import java.util.List;

public record DelegationRequest(List<SubAgentTask> tasks) {

    public DelegationRequest {
        tasks = List.copyOf(tasks);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must not be empty");
        }
    }
}
