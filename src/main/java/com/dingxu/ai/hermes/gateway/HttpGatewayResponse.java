package com.dingxu.ai.hermes.gateway;

public record HttpGatewayResponse(int status, GatewayTurnResponse body, String error) {

    public HttpGatewayResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be a valid HTTP status");
        }
        error = error == null ? "" : error;
    }

    public boolean ok() {
        return status >= 200 && status < 300;
    }
}
