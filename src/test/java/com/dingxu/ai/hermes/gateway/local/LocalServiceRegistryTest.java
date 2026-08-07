package com.dingxu.ai.hermes.gateway.local;

import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentState;
import com.dingxu.ai.hermes.core.FinishReason;
import com.dingxu.ai.hermes.gateway.feishu.FeishuEvent;
import com.dingxu.ai.hermes.gateway.feishu.FeishuEventHandler;
import com.dingxu.ai.hermes.gateway.feishu.FeishuHandleResult;
import com.dingxu.ai.hermes.gateway.feishu.FeishuHandleStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalServiceRegistryTest {

    @Test
    void registersAndInvokesTypedService() {
        LocalServiceRegistry registry = LocalServiceRegistry.empty()
                .register(new LocalServiceDefinition<>(
                        "uppercase",
                        String.class,
                        String.class,
                        String::toUpperCase
                ));

        String result = registry.invoke(
                "uppercase",
                "hermes",
                String.class
        );

        assertEquals("HERMES", result);
    }

    @Test
    void rejectsDuplicateNameAndWrongRequestType() {
        LocalServiceDefinition<String, String> service =
                new LocalServiceDefinition<>(
                        "uppercase",
                        String.class,
                        String.class,
                        String::toUpperCase
                );
        LocalServiceRegistry registry = LocalServiceRegistry.empty()
                .register(service);

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(service)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.invoke(
                        "uppercase",
                        42,
                        String.class
                )
        );
    }

    @Test
    void registersFeishuHandlerWithoutCopyingRuntimeLogic() {
        List<String> replies = new ArrayList<>();
        FeishuEventHandler handler = new FeishuEventHandler(
                request -> new AgentRunResult(
                        FinishReason.FINAL_ANSWER,
                        "done: " + request.userMessage(),
                        AgentState.start(request.userMessage())
                ),
                reply -> replies.add(reply.text())
        );
        LocalServiceRegistry registry = LocalServiceRegistry.empty();

        FeishuLocalService.register(registry, handler);
        FeishuHandleResult result = registry.invoke(
                FeishuLocalService.SERVICE_NAME,
                FeishuEvent.text(
                        "evt-1",
                        "chat-1",
                        "user-1",
                        "inspect README"
                ),
                FeishuHandleResult.class
        );

        assertEquals(FeishuHandleStatus.PROCESSED, result.status());
        assertEquals(List.of("done: inspect README"), replies);
    }
}
