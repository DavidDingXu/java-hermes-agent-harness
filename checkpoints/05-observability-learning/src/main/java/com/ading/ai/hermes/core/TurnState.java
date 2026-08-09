package com.ading.ai.hermes.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TurnState(
        String userMessage,
        List<AgentEvent> events,
        int modelTurns,
        List<ToolRequest> pendingToolCalls,
        FinishReason finishReason,
        String finalAnswer
) {

    public TurnState {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        events = List.copyOf(events);
        pendingToolCalls = pendingToolCalls == null ? List.of() : List.copyOf(pendingToolCalls);
        if (modelTurns < 0) {
            throw new IllegalArgumentException("modelTurns must not be negative");
        }
        finalAnswer = finalAnswer == null ? "" : finalAnswer;
    }

    public static TurnState start(String userMessage) {
        return new TurnState(
                userMessage,
                List.of(AgentEvent.userMessage(userMessage)),
                0,
                List.of(),
                null,
                ""
        );
    }

    public TurnState recordModelTurn(ModelTurn turn) {
        Objects.requireNonNull(turn, "turn must not be null");
        if (turn.kind() == ModelTurnKind.FINAL_ANSWER) {
            return new TurnState(
                    userMessage,
                    append(AgentEvent.modelFinalAnswer(turn.finalAnswer())),
                    modelTurns + 1,
                    List.of(),
                    FinishReason.FINAL_ANSWER,
                    turn.finalAnswer()
            );
        }
        return new TurnState(
                userMessage,
                appendAll(turn.toolRequests().stream().map(AgentEvent::toolRequested).toList()),
                modelTurns + 1,
                turn.toolRequests(),
                null,
                ""
        );
    }

    public TurnState recordToolObservation(ToolObservation observation) {
        return recordToolObservations(List.of(observation));
    }

    public TurnState recordToolObservations(List<ToolObservation> observations) {
        Objects.requireNonNull(observations, "observations must not be null");
        if (pendingToolCalls.isEmpty()) {
            throw new IllegalStateException("cannot record tool observation without a pending tool call");
        }
        if (observations.size() != pendingToolCalls.size()) {
            throw new IllegalArgumentException("tool observation count does not match pending tool calls");
        }
        for (int index = 0; index < pendingToolCalls.size(); index++) {
            if (!pendingToolCalls.get(index).callId().equals(observations.get(index).callId())) {
                throw new IllegalArgumentException(
                        "tool observation callId does not match pending tool call: "
                                + pendingToolCalls.get(index).callId()
                );
            }
        }
        return new TurnState(
                userMessage,
                appendAll(observations.stream().map(AgentEvent::toolObserved).toList()),
                modelTurns,
                List.of(),
                finishReason,
                finalAnswer
        );
    }

    public TurnState stop(FinishReason reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return new TurnState(userMessage, events, modelTurns, pendingToolCalls, reason, finalAnswer);
    }

    public Optional<ToolRequest> pendingToolCallOptional() {
        return pendingToolCalls.stream().findFirst();
    }

    public AgentState toAgentState() {
        return new AgentState(events, modelTurns);
    }

    private List<AgentEvent> append(AgentEvent event) {
        List<AgentEvent> next = new ArrayList<>(events);
        next.add(event);
        return next;
    }

    private List<AgentEvent> appendAll(List<AgentEvent> added) {
        List<AgentEvent> next = new ArrayList<>(events);
        next.addAll(added);
        return next;
    }
}
