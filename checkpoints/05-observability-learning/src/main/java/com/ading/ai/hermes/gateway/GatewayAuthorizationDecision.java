package com.ading.ai.hermes.gateway;

public record GatewayAuthorizationDecision(boolean allowed, boolean pairingRequired, String reason) {

    public GatewayAuthorizationDecision {
        reason = reason == null ? "" : reason;
        if (allowed && pairingRequired) {
            throw new IllegalArgumentException("an allowed decision cannot require pairing");
        }
    }

    public static GatewayAuthorizationDecision allow() {
        return new GatewayAuthorizationDecision(true, false, "");
    }

    public static GatewayAuthorizationDecision deny(String reason) {
        return new GatewayAuthorizationDecision(false, false, reason);
    }

    public static GatewayAuthorizationDecision requirePairing(String reason) {
        return new GatewayAuthorizationDecision(false, true, reason);
    }
}
