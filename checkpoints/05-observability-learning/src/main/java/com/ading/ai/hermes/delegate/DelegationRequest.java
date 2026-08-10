package com.ading.ai.hermes.delegate;

import java.util.List;
import java.util.UUID;

public record DelegationRequest(
        String delegationId,
        String parentRunId,
        String parentConversationId,
        List<SubAgentTask> tasks
) {

    public DelegationRequest {
        delegationId = requireText(delegationId, "delegationId");
        parentRunId = parentRunId == null ? "" : parentRunId.trim();
        parentConversationId = parentConversationId == null ? "" : parentConversationId.trim();
        tasks = List.copyOf(tasks);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must not be empty");
        }
    }

    public DelegationRequest(List<SubAgentTask> tasks) {
        this(UUID.randomUUID().toString(), "", "", tasks);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
