package com.dingxu.ai.hermes.session;

import com.dingxu.ai.hermes.core.AgentEvent;
import java.util.List;

public record SessionRecord(SessionId sessionId, List<AgentEvent> events) {

    public SessionRecord {
        events = List.copyOf(events);
    }
}
