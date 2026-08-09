package com.ading.ai.hermes.gateway;

@FunctionalInterface
public interface GatewayAuthorizationPolicy {

    GatewayAuthorizationDecision evaluate(GatewayIdentity identity);
}
