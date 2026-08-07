package com.dingxu.ai.hermes.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ErrorRecoveringAgentLoop {

    private final ModelDriver modelDriver;
    private final ToolDriver toolDriver;
    private final ErrorRecoveryPolicy recoveryPolicy;

    public ErrorRecoveringAgentLoop(
            ModelDriver modelDriver,
            ToolDriver toolDriver,
            ErrorRecoveryPolicy recoveryPolicy
    ) {
        this.modelDriver = Objects.requireNonNull(modelDriver, "modelDriver must not be null");
        this.toolDriver = Objects.requireNonNull(toolDriver, "toolDriver must not be null");
        this.recoveryPolicy = Objects.requireNonNull(recoveryPolicy, "recoveryPolicy must not be null");
    }

    public AgentRunResult run(AgentRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<AgentEvent> events = new ArrayList<>();
        events.add(AgentEvent.userMessage(request.userMessage()));
        int modelTurns = 0;
        int recoveries = 0;

        while (request.budget().allows(modelTurns)) {
            ModelTurn turn;
            try {
                turn = modelDriver.next(new AgentState(events, modelTurns));
            } catch (RuntimeException error) {
                if (recoveries >= recoveryPolicy.maxRecoveries()) {
                    return new AgentRunResult(
                            FinishReason.ERROR_LIMIT,
                            "",
                            new AgentState(events, modelTurns)
                    );
                }
                recoveries++;
                events.add(AgentEvent.errorRecovered("model error: " + describe(error)));
                continue;
            }

            modelTurns++;
            if (turn.kind() == ModelTurnKind.FINAL_ANSWER) {
                events.add(AgentEvent.modelFinalAnswer(turn.finalAnswer()));
                return new AgentRunResult(
                        FinishReason.FINAL_ANSWER,
                        turn.finalAnswer(),
                        new AgentState(events, modelTurns)
                );
            }

            events.addAll(turn.toolRequests().stream().map(AgentEvent::toolRequested).toList());
            List<ToolObservation> observations;
            try {
                observations = toolDriver.executeBatch(turn.toolRequests());
            } catch (RuntimeException error) {
                observations = turn.toolRequests().stream()
                        .map(toolRequest -> ToolObservation.failure(
                                toolRequest.callId(),
                                "recoverable tool error: " + describe(error)
                        ))
                        .toList();
                events.addAll(observations.stream().map(AgentEvent::toolObserved).toList());
                if (recoveries >= recoveryPolicy.maxRecoveries()) {
                    return new AgentRunResult(
                            FinishReason.ERROR_LIMIT,
                            "",
                            new AgentState(events, modelTurns)
                    );
                }
                recoveries++;
                continue;
            }
            events.addAll(observations.stream().map(AgentEvent::toolObserved).toList());
        }

        return new AgentRunResult(
                FinishReason.ITERATION_LIMIT,
                "",
                new AgentState(events, modelTurns)
        );
    }

    private String describe(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + message;
    }
}
