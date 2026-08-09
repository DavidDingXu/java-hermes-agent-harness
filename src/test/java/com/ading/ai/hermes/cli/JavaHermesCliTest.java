package com.ading.ai.hermes.cli;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaHermesCliTest {

    @Test
    void runsPromptThroughRuntimeAndPrintsFinalAnswer() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<String> sessionId = new AtomicReference<>();
        AtomicReference<Integer> turns = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JavaHermesCli cli = new JavaHermesCli(request -> {
            prompt.set(request.userMessage());
            sessionId.set(request.conversationId());
            turns.set(request.budget().maxTurns());
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "project inspected",
                    new AgentState(List.of(
                            AgentEvent.userMessage(request.userMessage()),
                            AgentEvent.modelFinalAnswer("project inspected")
                    ), 1)
            );
        }, stream(output), stream(new ByteArrayOutputStream()));

        int exitCode = cli.run(
                "--prompt", "inspect project",
                "--max-turns", "4",
                "--session", "reader-session"
        );

        assertEquals(0, exitCode);
        assertEquals("inspect project", prompt.get());
        assertEquals("reader-session", sessionId.get());
        assertEquals(4, turns.get());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("project inspected"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("sessionId=reader-session"));
    }

    @Test
    void printsToolNamesAndStatusWithoutArgumentsOrObservationContent() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JavaHermesCli cli = new JavaHermesCli(request -> new AgentRunResult(
                FinishReason.FINAL_ANSWER,
                "done",
                new AgentState(List.of(
                        AgentEvent.userMessage(request.userMessage()),
                        AgentEvent.toolRequested(new ToolRequest(
                                "call-1", "read_file", Map.of("path", "secret-name.txt")
                        )),
                        AgentEvent.toolObserved(ToolObservation.success(
                                "call-1", "private file content"
                        )),
                        AgentEvent.modelFinalAnswer("done")
                ), 2)
        ), stream(output), stream(new ByteArrayOutputStream()));

        assertEquals(0, cli.run("--prompt", "inspect", "--session", "reader-session"));

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("toolRequest=read_file callId=call-1"));
        assertTrue(text.contains("toolObservation=call-1 success=true"));
        assertFalse(text.contains("secret-name.txt"));
        assertFalse(text.contains("private file content"));
    }

    @Test
    void printsRedactedRuntimeErrorDetail() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JavaHermesCli cli = new JavaHermesCli(request -> new AgentRunResult(
                FinishReason.ERROR_LIMIT,
                "",
                new AgentState(List.of(
                        AgentEvent.userMessage(request.userMessage()),
                        AgentEvent.errorRecovered(
                                "model provider returned HTTP 401, api_key=sk-reader-secret"
                        )
                ), 0)
        ), stream(output), stream(new ByteArrayOutputStream()));

        assertEquals(1, cli.run("--prompt", "inspect", "--session", "reader-session"));

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("runtimeEvent=ERROR_RECOVERED detail="));
        assertTrue(text.contains("HTTP 401"));
        assertTrue(text.contains("api_key=[REDACTED]"));
        assertFalse(text.contains("sk-reader-secret"));
    }

    @Test
    void rejectsMissingPromptBeforeRuntime() {
        AtomicInteger calls = new AtomicInteger();
        JavaHermesCli cli = new JavaHermesCli(request -> {
            calls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        }, stream(new ByteArrayOutputStream()), stream(new ByteArrayOutputStream()));

        assertEquals(2, cli.run("--max-turns", "3"));
        assertEquals(0, calls.get());
    }

    @Test
    void showsHelpWithoutCallingRuntime() {
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JavaHermesCli cli = new JavaHermesCli(request -> {
            calls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        }, stream(output), stream(new ByteArrayOutputStream()));

        assertEquals(0, cli.run("--help"));
        assertEquals(0, calls.get());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Usage:"));
        assertTrue(JavaHermesCli.isHelpRequest("--help"));
    }

    @Test
    void rejectsUnsafeSessionIdBeforeRuntime() {
        AtomicInteger calls = new AtomicInteger();
        JavaHermesCli cli = new JavaHermesCli(request -> {
            calls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        }, stream(new ByteArrayOutputStream()), stream(new ByteArrayOutputStream()));

        assertEquals(2, cli.run("--prompt", "inspect", "--session", "../other"));
        assertEquals(0, calls.get());
    }

    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }
}
