package com.ading.ai.hermes.gateway;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAccessPolicyTest {

    private final GatewayIdentity identity = new GatewayIdentity(
            "feishu", "group", "chat-7", "user-9"
    );

    @Test
    void appliesChannelAllowAllAllowlistPairingAndGlobalFallbackInOrder() {
        assertTrue(GatewayAccessPolicy.channelAllowAll().evaluate(identity).allowed());
        assertTrue(GatewayAccessPolicy.allowList(Set.of("user-9")).evaluate(identity).allowed());
        assertTrue(GatewayAccessPolicy.paired(Set.of("feishu:user-9")).evaluate(identity).allowed());
        assertTrue(GatewayAccessPolicy.globalAllowAll().evaluate(identity).allowed());
        assertFalse(GatewayAccessPolicy.denyAll().evaluate(identity).allowed());
    }

    @Test
    void reportsPairingAsAnExplicitIntermediateDecision() {
        GatewayAuthorizationDecision decision = GatewayAccessPolicy.pairingRequired()
                .evaluate(identity);

        assertFalse(decision.allowed());
        assertTrue(decision.pairingRequired());
    }
}
