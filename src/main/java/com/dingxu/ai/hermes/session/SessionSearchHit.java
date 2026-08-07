package com.dingxu.ai.hermes.session;

import com.dingxu.ai.hermes.core.AgentEventKind;
import java.util.Objects;

public record SessionSearchHit(
        SessionId sessionId,
        int eventIndex,
        AgentEventKind kind,
        String snippet
) {

    public SessionSearchHit {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(snippet, "snippet must not be null");
        if (eventIndex < 0) {
            throw new IllegalArgumentException("eventIndex must not be negative");
        }
    }
}
