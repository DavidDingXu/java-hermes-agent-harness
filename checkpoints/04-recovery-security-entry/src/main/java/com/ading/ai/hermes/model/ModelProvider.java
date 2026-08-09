package com.ading.ai.hermes.model;

@FunctionalInterface
public interface ModelProvider {

    ChatResponse complete(ChatRequest request);
}
