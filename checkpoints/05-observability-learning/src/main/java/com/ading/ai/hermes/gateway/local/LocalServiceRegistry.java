package com.ading.ai.hermes.gateway.local;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalServiceRegistry {

    private final Map<String, LocalServiceDefinition<?, ?>> services =
            new LinkedHashMap<>();

    private LocalServiceRegistry() {
    }

    public static LocalServiceRegistry empty() {
        return new LocalServiceRegistry();
    }

    public LocalServiceRegistry register(
            LocalServiceDefinition<?, ?> service
    ) {
        if (services.putIfAbsent(service.name(), service) != null) {
            throw new IllegalArgumentException(
                    "local service already registered: " + service.name()
            );
        }
        return this;
    }

    public <O> O invoke(
            String name,
            Object request,
            Class<O> responseType
    ) {
        LocalServiceDefinition<?, ?> service = services.get(name);
        if (service == null) {
            throw new IllegalArgumentException(
                    "local service is not registered: " + name
            );
        }
        if (!service.responseType().equals(responseType)) {
            throw new IllegalArgumentException(
                    "local service " + name + " returns "
                            + service.responseType().getName()
            );
        }
        return responseType.cast(service.invoke(request));
    }
}
