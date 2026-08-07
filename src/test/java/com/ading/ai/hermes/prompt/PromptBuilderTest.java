package com.ading.ai.hermes.prompt;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.model.ChatRequest;
import com.ading.ai.hermes.model.ChatRole;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.ToolSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void buildsChatRequestWithSystemMessageUserMessageToolsAndOptions() {
        PromptBuilder builder = new PromptBuilder(
                PromptPolicy.hermesDefault(),
                List.of(new ToolSpec("read_file", "Read file", Map.of("path", "string"))),
                new ModelOptions("test-model", 0.0)
        );

        ChatRequest request = builder.create(AgentState.start("inspect README"));

        assertEquals("test-model", request.options().model());
        assertEquals(List.of(new ToolSpec("read_file", "Read file", Map.of("path", "string"))), request.tools());
        assertEquals(ChatRole.SYSTEM, request.messages().get(0).role());
        assertTrue(request.messages().get(0).content().contains("You are a Hermes-style agent runtime"));
        assertEquals(ChatRole.USER, request.messages().get(1).role());
        assertEquals("inspect README", request.messages().get(1).content());
    }

    @Test
    void convertsToolEventsIntoAssistantAndToolMessages() {
        PromptBuilder builder = new PromptBuilder(
                new PromptPolicy("Follow runtime boundaries."),
                List.of(),
                new ModelOptions("test-model", 0.0)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("read README"),
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "README content"))
        ), 1);

        ChatRequest request = builder.create(state);

        assertEquals(ChatRole.ASSISTANT, request.messages().get(2).role());
        assertEquals("", request.messages().get(2).content());
        assertEquals(
                List.of(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                request.messages().get(2).toolRequests()
        );
        assertEquals(ChatRole.TOOL, request.messages().get(3).role());
        assertEquals("call-1", request.messages().get(3).toolCallId());
        assertEquals("README content", request.messages().get(3).content());
    }

    @Test
    void includesBlockedToolObservationAsContext() {
        PromptBuilder builder = new PromptBuilder(
                new PromptPolicy("Follow runtime boundaries."),
                List.of(),
                new ModelOptions("test-model", 0.0)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("run shell"),
                AgentEvent.toolObserved(ToolObservation.failure(
                        "call-1",
                        "tool request blocked: shell execution requires approval"
                ))
        ), 1);

        ChatRequest request = builder.create(state);

        assertEquals(ChatRole.TOOL, request.messages().get(2).role());
        assertEquals("call-1", request.messages().get(2).toolCallId());
        assertEquals(
                "tool request failed: tool request blocked: shell execution requires approval",
                request.messages().get(2).content()
        );
    }

    @Test
    void convertsContextSummaryIntoSystemMessage() {
        PromptBuilder builder = new PromptBuilder(
                new PromptPolicy("Follow runtime boundaries."),
                List.of(),
                new ModelOptions("test-model", 0.0)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("continue"),
                AgentEvent.contextSummary("REFERENCE ONLY\nold tool result")
        ), 1);

        ChatRequest request = builder.create(state);

        assertEquals(ChatRole.SYSTEM, request.messages().get(2).role());
        assertEquals("context summary\nREFERENCE ONLY\nold tool result", request.messages().get(2).content());
    }

    @Test
    void convertsRunInterruptedEventIntoSystemMessage() {
        PromptBuilder builder = new PromptBuilder(
                new PromptPolicy("Follow runtime boundaries."),
                List.of(),
                new ModelOptions("test-model", 0.0)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("continue"),
                AgentEvent.runInterrupted("user sent /stop")
        ), 1);

        ChatRequest request = builder.create(state);

        assertEquals(ChatRole.SYSTEM, request.messages().get(2).role());
        assertEquals("run interrupted\nuser sent /stop", request.messages().get(2).content());
    }
}
