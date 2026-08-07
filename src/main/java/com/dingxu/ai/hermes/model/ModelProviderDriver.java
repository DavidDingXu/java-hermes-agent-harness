package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.AgentState;
import com.dingxu.ai.hermes.core.ModelDriver;
import com.dingxu.ai.hermes.core.ModelTurn;
import java.util.Objects;

public final class ModelProviderDriver implements ModelDriver {

    private final ModelProvider provider;
    private final ChatRequestFactory requestFactory;

    public ModelProviderDriver(ModelProvider provider, ChatRequestFactory requestFactory) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory must not be null");
    }

    @Override
    public ModelTurn next(AgentState state) {
        ChatRequest request = requestFactory.create(state);
        return provider.complete(request).turn();
    }
}
