package com.ading.ai.hermes.core;

import java.util.ArrayList;
import java.util.List;

public record AgentState(List<AgentEvent> events, int turnsUsed) {

    public AgentState {
        events = List.copyOf(events);
        if (turnsUsed < 0) {
            throw new IllegalArgumentException("turnsUsed must not be negative");
        }
    }

    public static AgentState start(String userMessage) {
        return new AgentState(List.of(AgentEvent.userMessage(userMessage)), 0);
    }

    public AgentState append(AgentEvent event) {
        List<AgentEvent> next = new ArrayList<>(events);
        next.add(event);
        return new AgentState(next, turnsUsed);
    }

    public AgentState incrementTurns() {
        return new AgentState(events, turnsUsed + 1);
    }
}
