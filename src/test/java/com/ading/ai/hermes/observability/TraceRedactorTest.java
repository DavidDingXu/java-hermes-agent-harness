package com.ading.ai.hermes.observability;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceRedactorTest {

    @Test
    void redactsSecretsRecursivelyInStructuredArguments() {
        Map<String, Object> redacted = new TraceRedactor().redactMap(Map.of(
                "headers", Map.of("Authorization", "Bearer private-token"),
                "items", List.of("safe", "password=secret-value"),
                "apiKey", "ordinary-looking-secret",
                "credentials", Map.of("refresh_token", "plain-value")
        ));

        assertEquals("[REDACTED]", ((Map<?, ?>) redacted.get("headers")).get("Authorization"));
        assertEquals(List.of("safe", "password=[REDACTED]"), redacted.get("items"));
        assertEquals("[REDACTED]", redacted.get("apiKey"));
        assertEquals("[REDACTED]", ((Map<?, ?>) redacted.get("credentials")).get("refresh_token"));
    }
}
