package com.ading.ai.hermes.programmatic;

import com.ading.ai.hermes.core.ToolObservation;
import java.util.Map;

@FunctionalInterface
public interface ProgrammaticToolContext {

    ToolObservation call(String toolName, Map<String, Object> arguments);
}
