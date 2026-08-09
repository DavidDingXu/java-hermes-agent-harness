package com.ading.ai.hermes.observability;

public enum TraceEventKind {
    USER_MESSAGE,
    TOOL_REQUESTED,
    TOOL_OBSERVED,
    ERROR_RECOVERED,
    MODEL_FINAL_ANSWER,
    RUN_INTERRUPTED,
    RUN_FINISHED,
    SUBAGENT_STOP
}
