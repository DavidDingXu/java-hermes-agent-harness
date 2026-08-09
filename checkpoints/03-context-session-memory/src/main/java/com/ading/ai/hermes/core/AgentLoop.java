package com.ading.ai.hermes.core;

import java.util.Objects;

public final class AgentLoop {

    private final ModelDriver modelDriver;
    private final ToolDriver toolDriver;
    private final TurnFinalizer turnFinalizer;

    public AgentLoop(ModelDriver modelDriver, ToolDriver toolDriver) {
        this.modelDriver = Objects.requireNonNull(modelDriver, "modelDriver must not be null");
        this.toolDriver = Objects.requireNonNull(toolDriver, "toolDriver must not be null");
        this.turnFinalizer = new TurnFinalizer();
    }

    public AgentRunResult run(AgentRunRequest request) {
        return run(request, new AgentState(java.util.List.of(), 0));
    }

    public AgentRunResult run(AgentRunRequest request, AgentState history) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(history, "history must not be null");
        TurnState turnState = TurnState.resume(request.userMessage(), history);
        while (request.budget().allows(turnState.modelTurns())) {
            ModelTurn turn = modelDriver.next(turnState.toAgentState());
            turnState = turnState.recordModelTurn(turn);
            if (turn.kind() == ModelTurnKind.FINAL_ANSWER) {
                return turnFinalizer.complete(turnState);
            }

            turnState = turnState.recordToolObservations(
                    toolDriver.executeBatch(turn.toolRequests())
            );
        }
        return turnFinalizer.stop(turnState, FinishReason.ITERATION_LIMIT);
    }
}
