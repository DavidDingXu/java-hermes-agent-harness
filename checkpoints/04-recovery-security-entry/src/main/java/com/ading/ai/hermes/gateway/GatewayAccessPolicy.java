package com.ading.ai.hermes.gateway;

import java.util.Objects;
import java.util.Set;

public final class GatewayAccessPolicy implements GatewayAuthorizationPolicy {

    private final boolean channelAllowAll;
    private final Set<String> allowedUsers;
    private final Set<String> pairedPrincipals;
    private final boolean requestPairing;
    private final boolean globalAllowAll;

    public GatewayAccessPolicy(
            boolean channelAllowAll,
            Set<String> allowedUsers,
            Set<String> pairedPrincipals,
            boolean requestPairing,
            boolean globalAllowAll
    ) {
        this.channelAllowAll = channelAllowAll;
        this.allowedUsers = Set.copyOf(Objects.requireNonNull(allowedUsers));
        this.pairedPrincipals = Set.copyOf(Objects.requireNonNull(pairedPrincipals));
        this.requestPairing = requestPairing;
        this.globalAllowAll = globalAllowAll;
    }

    @Override
    public GatewayAuthorizationDecision evaluate(GatewayIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (channelAllowAll || allowedUsers.contains(identity.userId())) {
            return GatewayAuthorizationDecision.allow();
        }
        if (pairedPrincipals.contains(identity.principalKey())) {
            return GatewayAuthorizationDecision.allow();
        }
        if (requestPairing) {
            return GatewayAuthorizationDecision.requirePairing("当前账号需要先完成配对");
        }
        if (globalAllowAll) {
            return GatewayAuthorizationDecision.allow();
        }
        return GatewayAuthorizationDecision.deny("当前账号未授权访问 Hermes");
    }

    public static GatewayAccessPolicy channelAllowAll() {
        return new GatewayAccessPolicy(true, Set.of(), Set.of(), false, false);
    }

    public static GatewayAccessPolicy allowList(Set<String> users) {
        return new GatewayAccessPolicy(false, users, Set.of(), false, false);
    }

    public static GatewayAccessPolicy paired(Set<String> principals) {
        return new GatewayAccessPolicy(false, Set.of(), principals, false, false);
    }

    public static GatewayAccessPolicy pairingRequired() {
        return new GatewayAccessPolicy(false, Set.of(), Set.of(), true, false);
    }

    public static GatewayAccessPolicy globalAllowAll() {
        return new GatewayAccessPolicy(false, Set.of(), Set.of(), false, true);
    }

    public static GatewayAccessPolicy denyAll() {
        return new GatewayAccessPolicy(false, Set.of(), Set.of(), false, false);
    }
}
