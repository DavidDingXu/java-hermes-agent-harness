package com.ading.ai.hermes.hook;

import java.util.Map;

public record RuntimeHookEvent(
        RuntimeHookPoint point,
        String runId,
        String subject,
        Map<String, Object> payload
) {
    public RuntimeHookEvent {
        payload = Map.copyOf(payload);
    }

    public RuntimeHookEvent withPayload(Map<String, Object> nextPayload) {
        return new RuntimeHookEvent(point, runId, subject, nextPayload);
    }
}
