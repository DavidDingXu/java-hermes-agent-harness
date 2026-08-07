package com.ading.ai.hermes.context;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEngineTest {

    @Test
    void selectsCompactedContextAndObservesCompletedTurn() {
        List<AgentRunResult> completed = new ArrayList<>();
        ContextEngine engine = new CompactingContextEngine(
                new ContextCompactor(new ContextCompactionPolicy(80, 1, 1, 120, 40)),
                completed::add
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("inspect the project"),
                AgentEvent.errorRecovered("x".repeat(120)),
                AgentEvent.modelFinalAnswer("done")
        ), 1);

        ContextCompactionResult selection = engine.select(state);
        AgentRunResult result = new AgentRunResult(FinishReason.FINAL_ANSWER, "done", selection.state());
        engine.onTurnCompleted(result);

        assertTrue(selection.compacted());
        assertEquals(1, completed.size());
        assertEquals("done", completed.get(0).finalAnswer());
    }
}
