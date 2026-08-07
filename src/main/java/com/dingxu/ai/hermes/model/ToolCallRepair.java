package com.dingxu.ai.hermes.model;

import java.util.Objects;

public record ToolCallRepair(ToolCallRepairKind kind, String originalCallId, String repairedCallId, String message) {

    public ToolCallRepair {
        Objects.requireNonNull(kind, "kind must not be null");
        originalCallId = originalCallId == null ? "" : originalCallId;
        if (repairedCallId == null || repairedCallId.isBlank()) {
            throw new IllegalArgumentException("repairedCallId must not be blank");
        }
        message = message == null ? "" : message;
    }
}
