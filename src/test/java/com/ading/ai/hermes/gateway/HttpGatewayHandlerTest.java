package com.ading.ai.hermes.gateway;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.control.AdmissionDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpGatewayHandlerTest {

    @Test
    void mapsHttpRequestToAgentRuntime() {
        AtomicReference<AgentRunRequest> capturedRequest = new AtomicReference<>();
        AgentRuntime runtime = request -> {
            capturedRequest.set(request);
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "gateway accepted",
                    new AgentState(List.of(
                            AgentEvent.userMessage(request.userMessage()),
                            AgentEvent.modelFinalAnswer("gateway accepted")
                    ), 1)
            );
        };
        HttpGatewayHandler handler = new HttpGatewayHandler(runtime);

        HttpGatewayResponse response = handler.handle(new HttpGatewayRequest(
                "POST",
                "/v1/turns",
                Map.of("X-Hermes-Session-Key", "agent:main:web:user-42"),
                new GatewayTurnRequest(
                        "web-ui",
                        "transcript-alpha",
                        "read the project roadmap",
                        Map.of("client", "test")
                )
        ));

        assertEquals(200, response.status());
        assertEquals("transcript-alpha", response.body().conversationId());
        assertEquals("agent:main:web:user-42", response.body().sessionKey());
        assertEquals("gateway accepted", response.body().finalAnswer());
        assertEquals(FinishReason.FINAL_ANSWER, response.body().finishReason());
        assertEquals("web-ui", capturedRequest.get().source());
        assertEquals("transcript-alpha", capturedRequest.get().conversationId());
        assertEquals("read the project roadmap", capturedRequest.get().userMessage());
        assertEquals("test", capturedRequest.get().metadata().get("client"));
        assertEquals(6, capturedRequest.get().budget().maxTurns());
        assertTrue(response.error().isBlank());
    }

    @Test
    void rejectsUnsupportedMethodBeforeRuntime() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        HttpGatewayHandler handler = new HttpGatewayHandler(request -> {
            runtimeCalls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        });

        HttpGatewayResponse response = handler.handle(new HttpGatewayRequest(
                "GET",
                "/v1/turns",
                Map.of(),
                new GatewayTurnRequest("web-ui", "c1", "hello", Map.of())
        ));

        assertEquals(405, response.status());
        assertEquals(0, runtimeCalls.get());
        assertFalse(response.error().isBlank());
    }

    @Test
    void rejectsUnknownPathBeforeRuntime() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        HttpGatewayHandler handler = new HttpGatewayHandler(request -> {
            runtimeCalls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        });

        HttpGatewayResponse response = handler.handle(new HttpGatewayRequest(
                "POST",
                "/api/unknown",
                Map.of(),
                new GatewayTurnRequest("web-ui", "c1", "hello", Map.of())
        ));

        assertEquals(404, response.status());
        assertEquals(0, runtimeCalls.get());
        assertFalse(response.error().isBlank());
    }

    @Test
    void rejectsInvalidTurnBeforeRuntime() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        HttpGatewayHandler handler = new HttpGatewayHandler(request -> {
            runtimeCalls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        });

        HttpGatewayResponse response = handler.handle(new HttpGatewayRequest(
                "POST",
                "/v1/turns",
                Map.of(),
                new GatewayTurnRequest("web-ui", "c1", "   ", Map.of())
        ));

        assertEquals(400, response.status());
        assertEquals(0, runtimeCalls.get());
        assertFalse(response.error().isBlank());
    }

    @Test
    void rejectsValidNewWorkWhileAdmissionIsPaused() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        HttpGatewayHandler handler = new HttpGatewayHandler(
                request -> {
                    runtimeCalls.incrementAndGet();
                    return new AgentRunResult(
                            FinishReason.FINAL_ANSWER,
                            "unused",
                            AgentState.start(request.userMessage())
                    );
                },
                IterationBudget.maxTurns(6),
                () -> AdmissionDecision.reject("maintenance")
        );

        HttpGatewayResponse response = handler.handle(new HttpGatewayRequest(
                "POST",
                "/v1/turns",
                Map.of(),
                new GatewayTurnRequest("web-ui", "c1", "hello", Map.of())
        ));

        assertEquals(503, response.status());
        assertEquals("maintenance", response.error());
        assertEquals(0, runtimeCalls.get());
    }
}
