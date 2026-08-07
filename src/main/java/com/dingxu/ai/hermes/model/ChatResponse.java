package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.ModelTurn;
import java.util.Objects;

public record ChatResponse(ModelTurn turn, Usage usage, String provider, String reasoning) {

    public ChatResponse(ModelTurn turn, Usage usage, String provider) {
        this(turn, usage, provider, "");
    }

    public ChatResponse {
        Objects.requireNonNull(turn, "turn must not be null");
        usage = usage == null ? Usage.empty() : usage;
        provider = provider == null ? "" : provider;
        reasoning = reasoning == null ? "" : reasoning;
    }

    public static ChatResponse of(ModelTurn turn) {
        return new ChatResponse(turn, Usage.empty(), "scripted", "");
    }
}
