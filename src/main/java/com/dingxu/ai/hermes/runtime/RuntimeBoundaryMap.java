package com.dingxu.ai.hermes.runtime;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public record RuntimeBoundaryMap(List<RuntimeBoundary> boundaries) {

    public RuntimeBoundaryMap {
        boundaries = List.copyOf(boundaries);
        EnumSet<BoundaryKind> seen = EnumSet.noneOf(BoundaryKind.class);
        for (RuntimeBoundary boundary : boundaries) {
            if (!seen.add(boundary.kind())) {
                throw new IllegalArgumentException("runtime boundary kind must be unique: " + boundary.kind());
            }
        }
    }

    public static RuntimeBoundaryMap minimalHermesStyle() {
        List<RuntimeBoundary> boundaries = new ArrayList<>();
        boundaries.add(new RuntimeBoundary(
                BoundaryKind.ENTRY,
                "Entry",
                "CLI, gateway, scheduled job, or resumed turn",
                "normalized turn request",
                "Convert every external trigger into one runtime request shape.",
                "README lists CLI, messaging gateway, cron scheduling, and interrupt/resume surfaces."
        ));
        boundaries.add(new RuntimeBoundary(
                BoundaryKind.MODEL,
                "Model",
                "messages, tools, model parameters, and provider config",
                "assistant message, tool calls, usage, or provider error",
                "Hide provider differences behind one model-call contract.",
                "agent/conversation_loop.py delegates model calls through provider adapters instead of owning provider-specific HTTP behavior."
        ));
        boundaries.add(new RuntimeBoundary(
                BoundaryKind.TOOL,
                "Tool",
                "tool call name, call id, and JSON arguments",
                "structured tool result or structured tool error",
                "Expose real actions through a registry, schema, dispatcher, and execution result.",
                "toolsets.py, model_tools.py, and agent/tool_dispatch_helpers.py define tool surfaces and dispatch rules."
        ));
        boundaries.add(new RuntimeBoundary(
                BoundaryKind.CONTEXT,
                "Context",
                "identity, environment hints, project files, memory, skills, and session slices",
                "bounded prompt payload for the next model call",
                "Assemble prompt sections with explicit order, safety checks, and context-window control.",
                "agent/prompt_builder.py and agent/context_compressor.py build and compact model context."
        ));
        boundaries.add(new RuntimeBoundary(
                BoundaryKind.SESSION,
                "Session",
                "turn events, messages, tool calls, observations, and checkpoints",
                "durable conversation state and resume cursor",
                "Persist enough state to search, resume, and explain a run without replaying everything.",
                "hermes_state.py and session-search features store conversation history and recall past sessions."
        ));
        boundaries.add(new RuntimeBoundary(
                BoundaryKind.SAFETY,
                "Safety",
                "tool request, context file, file path, command, or generated change",
                "allow, block, approval request, or sanitized context",
                "Keep model intent behind explicit permission and injection boundaries.",
                "agent/tool_guardrails.py, agent/file_safety.py, and prompt-builder context scanning guard tool and prompt inputs."
        ));
        boundaries.add(new RuntimeBoundary(
                BoundaryKind.OBSERVABILITY,
                "Observability",
                "model calls, tool calls, errors, usage, decisions, and final result",
                "trace event, trajectory, cost record, or review signal",
                "Make each run inspectable enough for debugging, evaluation, and later improvement.",
                "agent/trajectory.py, usage tracking, insights, and background review paths record run behavior."
        ));
        return new RuntimeBoundaryMap(boundaries);
    }
}
