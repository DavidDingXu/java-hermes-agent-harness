package com.dingxu.ai.hermes.gateway.local;

import java.util.Objects;
import java.util.function.Function;

public record LocalServiceDefinition<I, O>(
        String name,
        Class<I> requestType,
        Class<O> responseType,
        Function<I, O> handler
) {

    public LocalServiceDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(requestType, "requestType must not be null");
        Objects.requireNonNull(responseType, "responseType must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
    }

    Object invoke(Object request) {
        if (!requestType.isInstance(request)) {
            throw new IllegalArgumentException(
                    "service " + name + " expects " + requestType.getName()
            );
        }
        return handler.apply(requestType.cast(request));
    }
}
