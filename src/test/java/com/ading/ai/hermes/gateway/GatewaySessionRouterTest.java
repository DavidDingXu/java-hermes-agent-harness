package com.ading.ai.hermes.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewaySessionRouterTest {

    @Test
    void separatesPlatformsChatTypesAndChatsWithAStableKey() {
        GatewaySessionRouter router = new GatewaySessionRouter();

        assertEquals(
                "agent:main:feishu:group:chat-7",
                router.sessionKey(new GatewayIdentity("feishu", "group", "chat-7", "user-9"))
        );
        assertEquals(
                "agent:main:feishu:direct:chat-7",
                router.sessionKey(new GatewayIdentity("feishu", "direct", "chat-7", "user-9"))
        );
    }
}
