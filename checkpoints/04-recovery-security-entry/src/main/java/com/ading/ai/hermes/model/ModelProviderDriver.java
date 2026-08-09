package com.ading.ai.hermes.model;

import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ModelDriver;
import com.ading.ai.hermes.core.ModelTurn;
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
