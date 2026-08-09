package com.ading.ai.hermes.observability;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.delegate.DelegationResult;
import com.ading.ai.hermes.delegate.SubAgentResult;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TrajectoryRecorder {

    private final Clock clock;
    private final TraceRedactor redactor;

    public TrajectoryRecorder(Clock clock, TraceRedactor redactor) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.redactor = Objects.requireNonNull(redactor, "redactor must not be null");
    }

    public TrajectoryRecord recordRun(String sessionId, String turnId, AgentRunResult runResult) {
        Objects.requireNonNull(runResult, "runResult must not be null");
        String timestamp = now();
        List<TraceEvent> events = new ArrayList<>();
        for (AgentEvent event : runResult.state().events()) {
            TraceEvent traceEvent = toTraceEvent(sessionId, turnId, timestamp, event);
            if (traceEvent != null) {
                events.add(traceEvent);
            }
        }
        events.add(new TraceEvent(
                TraceEventKind.RUN_FINISHED,
                sessionId,
                turnId,
                "",
                "",
                timestamp,
                Map.of(
                        "finishReason", runResult.finishReason().name(),
                        "finalAnswer", redactor.redact(runResult.finalAnswer()),
                        "turnsUsed", String.valueOf(runResult.state().turnsUsed())
                )
        ));
        return new TrajectoryRecord(sessionId, turnId, timestamp, events);
    }

    public TrajectoryRecord recordDelegation(String sessionId, String turnId, DelegationResult delegationResult) {
        Objects.requireNonNull(delegationResult, "delegationResult must not be null");
        String timestamp = now();
        List<TraceEvent> events = new ArrayList<>();
        for (SubAgentResult result : delegationResult.results()) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("status", result.status().name());
            attributes.put("summary", redactor.redact(result.summary()));
            attributes.put("finishReason", result.finishReason().name());
            attributes.put("turnsUsed", String.valueOf(result.turnsUsed()));
            attributes.put("budgetMaxTurns", String.valueOf(result.budget().maxTurns()));
            attributes.put("toolsets", redactor.redact(String.valueOf(result.toolsets())));
            events.add(new TraceEvent(
                    TraceEventKind.SUBAGENT_STOP,
                    sessionId,
                    turnId,
                    result.taskId(),
                    turnId,
                    timestamp,
                    attributes
            ));
        }
        return new TrajectoryRecord(sessionId, turnId, timestamp, events);
    }

    private TraceEvent toTraceEvent(String sessionId, String turnId, String timestamp, AgentEvent event) {
        AgentEventKind kind = event.kind();
        return switch (kind) {
            case USER_MESSAGE -> event(TraceEventKind.USER_MESSAGE, sessionId, turnId, timestamp,
                    Map.of("text", redactor.redact(event.text())));
            case TOOL_REQUESTED -> event(TraceEventKind.TOOL_REQUESTED, sessionId, turnId, timestamp,
                    Map.of(
                            "callId", event.toolRequest().callId(),
                            "toolName", event.toolRequest().name(),
                            "arguments", redactor.redact(String.valueOf(event.toolRequest().arguments()))
                    ));
            case TOOL_OBSERVED -> event(TraceEventKind.TOOL_OBSERVED, sessionId, turnId, timestamp,
                    Map.of(
                            "callId", event.toolObservation().callId(),
                            "success", String.valueOf(event.toolObservation().success()),
                            "content", redactor.redact(event.toolObservation().content())
                    ));
            case MODEL_FINAL_ANSWER -> event(TraceEventKind.MODEL_FINAL_ANSWER, sessionId, turnId, timestamp,
                    Map.of("text", redactor.redact(event.text())));
            case ERROR_RECOVERED -> event(TraceEventKind.ERROR_RECOVERED, sessionId, turnId, timestamp,
                    Map.of("message", redactor.redact(event.text())));
            case RUN_INTERRUPTED -> event(TraceEventKind.RUN_INTERRUPTED, sessionId, turnId, timestamp,
                    Map.of("reason", redactor.redact(event.text())));
            case CONTEXT_SUMMARY -> null;
        };
    }

    private TraceEvent event(
            TraceEventKind kind,
            String sessionId,
            String turnId,
            String timestamp,
            Map<String, String> attributes
    ) {
        return new TraceEvent(kind, sessionId, turnId, "", "", timestamp, attributes);
    }

    private String now() {
        Instant instant = clock.instant();
        return instant.toString();
    }
}
