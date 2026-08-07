package com.dingxu.ai.hermes.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

public final class ScriptedModelProvider implements ModelProvider {

    private final Queue<ChatResponse> responses;
    private final List<ChatRequest> requests = new ArrayList<>();

    public ScriptedModelProvider(ChatResponse... responses) {
        this.responses = new ArrayDeque<>(Arrays.asList(responses));
        if (this.responses.isEmpty()) {
            throw new IllegalArgumentException("responses must not be empty");
        }
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        requests.add(request);
        if (responses.isEmpty()) {
            return ChatResponse.of(com.dingxu.ai.hermes.core.ModelTurn.finalAnswer("script exhausted"));
        }
        return responses.remove();
    }

    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }
}
