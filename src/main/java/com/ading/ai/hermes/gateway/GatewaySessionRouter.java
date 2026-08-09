package com.ading.ai.hermes.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class GatewaySessionRouter {

    public String sessionKey(GatewayIdentity identity) {
        return "agent:main:"
                + segment(identity.platform()) + ":"
                + segment(identity.chatType()) + ":"
                + segment(identity.chatId());
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
