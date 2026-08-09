package com.ading.ai.hermes.core;

public enum AgentEventKind {
    USER_MESSAGE,
    CONTEXT_SUMMARY,
    ERROR_RECOVERED,
    RUN_INTERRUPTED,
    MODEL_FINAL_ANSWER,
    TOOL_REQUESTED,
    TOOL_OBSERVED
}
