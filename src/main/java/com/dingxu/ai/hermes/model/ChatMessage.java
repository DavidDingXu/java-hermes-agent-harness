package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Objects;

public record ChatMessage(
        ChatRole role,
        String content,
        List<ToolRequest> toolRequests,
        String toolCallId
) {

    public ChatMessage {
        Objects.requireNonNull(role, "role must not be null");
        content = content == null ? "" : content;
        toolRequests = toolRequests == null ? List.of() : List.copyOf(toolRequests);
        toolCallId = toolCallId == null ? "" : toolCallId;
        if (role != ChatRole.ASSISTANT && !toolRequests.isEmpty()) {
            throw new IllegalArgumentException("only assistant messages may contain tool requests");
        }
        if (role != ChatRole.TOOL && !toolCallId.isBlank()) {
            throw new IllegalArgumentException("only tool messages may contain a tool call id");
        }
        if (role == ChatRole.TOOL && toolCallId.isBlank()) {
            throw new IllegalArgumentException("tool message must contain a tool call id");
        }
        if (role != ChatRole.TOOL && content.isBlank() && toolRequests.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    public ChatMessage(ChatRole role, String content) {
        this(role, content, List.of(), "");
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatRole.SYSTEM, content, List.of(), "");
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content, List.of(), "");
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content, List.of(), "");
    }

    public static ChatMessage assistantToolCalls(List<ToolRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("assistant tool calls must not be empty");
        }
        return new ChatMessage(ChatRole.ASSISTANT, "", requests, "");
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage(ChatRole.TOOL, content, List.of(), toolCallId);
    }
}
