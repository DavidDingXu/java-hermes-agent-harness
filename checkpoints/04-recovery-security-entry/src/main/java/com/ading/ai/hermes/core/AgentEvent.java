package com.ading.ai.hermes.core;

import java.util.Objects;

public record AgentEvent(AgentEventKind kind, String text, ToolRequest toolRequest, ToolObservation toolObservation) {

    public AgentEvent {
        Objects.requireNonNull(kind, "kind must not be null");
        text = text == null ? "" : text;
    }

    public static AgentEvent userMessage(String text) {
        return new AgentEvent(AgentEventKind.USER_MESSAGE, text, null, null);
    }

    public static AgentEvent contextSummary(String text) {
        return new AgentEvent(AgentEventKind.CONTEXT_SUMMARY, text, null, null);
    }

    public static AgentEvent errorRecovered(String text) {
        return new AgentEvent(AgentEventKind.ERROR_RECOVERED, text, null, null);
    }

    public static AgentEvent completionRejected(String text) {
        return new AgentEvent(AgentEventKind.COMPLETION_REJECTED, text, null, null);
    }

    public static AgentEvent runInterrupted(String text) {
        return new AgentEvent(AgentEventKind.RUN_INTERRUPTED, text, null, null);
    }

    public static AgentEvent modelFinalAnswer(String text) {
        return new AgentEvent(AgentEventKind.MODEL_FINAL_ANSWER, text, null, null);
    }

    public static AgentEvent toolRequested(ToolRequest toolRequest) {
        return new AgentEvent(AgentEventKind.TOOL_REQUESTED, "", toolRequest, null);
    }

    public static AgentEvent toolObserved(ToolObservation observation) {
        return new AgentEvent(AgentEventKind.TOOL_OBSERVED, "", null, observation);
    }
}
