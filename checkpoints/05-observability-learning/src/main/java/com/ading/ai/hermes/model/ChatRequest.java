package com.ading.ai.hermes.model;

import java.util.List;
import java.util.Objects;

public record ChatRequest(
        List<ChatMessage> messages,
        List<ToolSpec> tools,
        ModelOptions options,
        PromptCacheDescriptor cache
) {

    public ChatRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(cache, "cache must not be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
    }

    public ChatRequest(List<ChatMessage> messages, List<ToolSpec> tools, ModelOptions options) {
        this(messages, tools, options, PromptCacheDescriptor.none());
    }
}
