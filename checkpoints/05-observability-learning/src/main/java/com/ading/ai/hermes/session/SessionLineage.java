package com.ading.ai.hermes.session;

import java.util.Objects;
import java.util.Optional;

public record SessionLineage(
        SessionId sessionId,
        String source,
        Optional<SessionId> parentSessionId,
        SessionId rootSessionId
) {

    public SessionLineage {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        source = source.trim();
        parentSessionId = Objects.requireNonNull(
                parentSessionId,
                "parentSessionId must not be null"
        );
        Objects.requireNonNull(rootSessionId, "rootSessionId must not be null");
    }
}
