package com.dingxu.ai.hermes.model;

@FunctionalInterface
public interface ModelProvider {

    ChatResponse complete(ChatRequest request);
}
