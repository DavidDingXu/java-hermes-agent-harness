package com.dingxu.ai.hermes.tool;

public record ToolResult(String callId, boolean success, String content) {

    public ToolResult {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        content = content == null ? "" : content;
    }

    public static ToolResult success(String callId, String content) {
        return new ToolResult(callId, true, content);
    }

    public static ToolResult failure(String callId, String content) {
        return new ToolResult(callId, false, content);
    }
}
