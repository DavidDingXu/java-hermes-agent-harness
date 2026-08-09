package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentEvent;
import java.util.List;

public record SessionRecord(SessionId sessionId, List<AgentEvent> events) {

    public SessionRecord {
        events = List.copyOf(events);
    }
}
