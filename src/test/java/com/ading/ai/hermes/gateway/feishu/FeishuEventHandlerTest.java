package com.ading.ai.hermes.gateway.feishu;

import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.control.AdmissionDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeishuEventHandlerTest {

    @Test
    void answersChallengeWithoutCallingRuntime() {
        AtomicInteger calls = new AtomicInteger();
        FeishuEventHandler handler = new FeishuEventHandler(request -> {
            calls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        }, reply -> { });

        FeishuHandleResult result = handler.handle(FeishuEvent.challenge("challenge-token"));

        assertEquals(FeishuHandleStatus.CHALLENGE, result.status());
        assertEquals("challenge-token", result.responseBody());
        assertEquals(0, calls.get());
    }

    @Test
    void mapsTextEventToRuntimeRepliesOnceAndIgnoresDuplicateEventId() {
        AtomicReference<AgentRunRequest> capturedRequest = new AtomicReference<>();
        List<FeishuReply> replies = new ArrayList<>();
        FeishuEventHandler handler = new FeishuEventHandler(request -> {
            capturedRequest.set(request);
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "analysis finished", AgentState.start(request.userMessage()));
        }, replies::add);
        FeishuEvent event = FeishuEvent.text("evt-1", "chat-7", "user-9", "inspect logs");

        FeishuHandleResult first = handler.handle(event);
        FeishuHandleResult duplicate = handler.handle(event);

        assertEquals(FeishuHandleStatus.PROCESSED, first.status());
        assertEquals(FeishuHandleStatus.DUPLICATE, duplicate.status());
        assertEquals("inspect logs", capturedRequest.get().userMessage());
        assertEquals("feishu", capturedRequest.get().source());
        assertEquals("chat-7", capturedRequest.get().conversationId());
        assertEquals("evt-1", capturedRequest.get().metadata().get("eventId"));
        assertEquals("user-9", capturedRequest.get().metadata().get("senderId"));
        assertEquals(List.of(new FeishuReply("chat-7", "analysis finished")), replies);
        assertTrue(first.responseBody().isBlank());
    }

    @Test
    void failedRuntimeCallDoesNotPoisonEventDeduplication() {
        AtomicInteger calls = new AtomicInteger();
        FeishuEventHandler handler = new FeishuEventHandler(request -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("temporary failure");
            }
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "retried", AgentState.start(request.userMessage()));
        }, reply -> { });
        FeishuEvent event = FeishuEvent.text("evt-retry", "chat-1", "user-1", "retry me");

        assertThrows(IllegalStateException.class, () -> handler.handle(event));
        assertEquals(FeishuHandleStatus.PROCESSED, handler.handle(event).status());
        assertEquals(2, calls.get());
    }

    @Test
    void rejectsNewTextEventsWhileGlobalAdmissionIsPaused() {
        AtomicInteger calls = new AtomicInteger();
        List<FeishuReply> replies = new ArrayList<>();
        FeishuEventHandler handler = new FeishuEventHandler(
                request -> {
                    calls.incrementAndGet();
                    return new AgentRunResult(
                            FinishReason.FINAL_ANSWER,
                            "unused",
                            AgentState.start(request.userMessage())
                    );
                },
                replies::add,
                () -> AdmissionDecision.reject("planned maintenance")
        );

        FeishuHandleResult result = handler.handle(
                FeishuEvent.text("evt-paused", "chat-1", "user-1", "inspect logs")
        );

        assertEquals(FeishuHandleStatus.REJECTED, result.status());
        assertEquals("planned maintenance", result.error());
        assertEquals(0, calls.get());
        assertTrue(replies.isEmpty());
    }
}
