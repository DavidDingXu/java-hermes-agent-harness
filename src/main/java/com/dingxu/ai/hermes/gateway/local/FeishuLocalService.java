package com.dingxu.ai.hermes.gateway.local;

import com.dingxu.ai.hermes.gateway.feishu.FeishuEvent;
import com.dingxu.ai.hermes.gateway.feishu.FeishuEventHandler;
import com.dingxu.ai.hermes.gateway.feishu.FeishuHandleResult;
import java.util.Objects;

public final class FeishuLocalService {

    public static final String SERVICE_NAME = "feishu.events";

    private FeishuLocalService() {
    }

    public static LocalServiceRegistry register(
            LocalServiceRegistry registry,
            FeishuEventHandler handler
    ) {
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        return registry.register(new LocalServiceDefinition<>(
                SERVICE_NAME,
                FeishuEvent.class,
                FeishuHandleResult.class,
                handler::handle
        ));
    }
}
