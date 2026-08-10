package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentLoop;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.ModelTurn;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckpointContractTest {

    @Test
    void closesOneModelToolModelLoop() {
        var turns = new ArrayDeque<>(List.of(
                ModelTurn.toolRequest(new ToolRequest("call-1", "marker", Map.of())),
                ModelTurn.finalAnswer("阶段 01 完成")
        ));
        AgentLoop loop = new AgentLoop(
                state -> turns.removeFirst(),
                request -> ToolObservation.success(request.callId(), "marker observed")
        );

        var result = loop.run(AgentRunRequest.start("验证主循环", IterationBudget.maxTurns(3)));

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_OBSERVED,
                AgentEventKind.MODEL_FINAL_ANSWER
        ), result.state().events().stream().map(event -> event.kind()).toList());
    }
}
