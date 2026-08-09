package com.ading.ai.hermes.gateway;

import java.util.Locale;
import java.util.Map;

public record HttpGatewayRequest(
        String method,
        String path,
        Map<String, String> headers,
        GatewayTurnRequest turnRequest
) {

    public HttpGatewayRequest {
        method = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        path = path == null ? "" : path.trim();
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public String header(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }
}
