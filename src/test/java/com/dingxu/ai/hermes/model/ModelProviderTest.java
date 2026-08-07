package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.AgentLoop;
import com.dingxu.ai.hermes.core.AgentRunRequest;
import com.dingxu.ai.hermes.core.FinishReason;
import com.dingxu.ai.hermes.core.IterationBudget;
import com.dingxu.ai.hermes.core.ModelTurn;
import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelProviderTest {

    @Test
    void fakeProviderCanDriveAgentLoopWithoutNetwork() {
        ScriptedModelProvider provider = new ScriptedModelProvider(
                ChatResponse.of(ModelTurn.toolRequest(new ToolRequest("call-1", "read_file", Map.of("path", "README.md")))),
                ChatResponse.of(ModelTurn.finalAnswer("README has been inspected."))
        );
        ModelProviderDriver driver = new ModelProviderDriver(
                provider,
                state -> new ChatRequest(
                        List.of(ChatMessage.user("inspect README")),
                        List.of(new ToolSpec("read_file", "Read a file from the workspace", Map.of("path", "string"))),
                        new ModelOptions("fake-model", 0.0)
                )
        );
        AgentLoop loop = new AgentLoop(driver, request -> ToolObservation.success(request.callId(), "file loaded"));

        var result = loop.run(new AgentRunRequest("inspect README", new IterationBudget(3)));

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals("README has been inspected.", result.finalAnswer());
        assertEquals(2, provider.requests().size());
        assertEquals("fake-model", provider.requests().get(0).options().model());
        assertEquals("read_file", provider.requests().get(0).tools().get(0).name());
    }
}
