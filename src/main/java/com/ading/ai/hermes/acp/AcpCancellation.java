package com.ading.ai.hermes.acp;

@FunctionalInterface
public interface AcpCancellation {

    void request(String sessionId);
}
