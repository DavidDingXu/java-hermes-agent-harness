package com.dingxu.ai.hermes.hook;

public enum RuntimeHookPoint {
    BEFORE_RUN,
    AFTER_RUN,
    BEFORE_MODEL,
    AFTER_MODEL,
    BEFORE_TOOL,
    TRANSFORM_TOOL_RESULT,
    AFTER_TOOL,
    SESSION_START,
    SESSION_END
}
