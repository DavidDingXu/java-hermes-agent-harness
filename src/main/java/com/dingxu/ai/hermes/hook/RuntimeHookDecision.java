package com.dingxu.ai.hermes.hook;

import java.util.List;
import java.util.Map;

public record RuntimeHookDecision(
        boolean allowed,
        Map<String, Object> payload,
        String reason,
        List<String> warnings
) {
    public RuntimeHookDecision {
        payload = Map.copyOf(payload);
        warnings = List.copyOf(warnings);
    }

    public static RuntimeHookDecision continueWith(Map<String, Object> payload) {
        return new RuntimeHookDecision(true, payload, "", List.of());
    }

    public static RuntimeHookDecision block(String reason, Map<String, Object> payload) {
        return new RuntimeHookDecision(false, payload, reason, List.of());
    }
}
