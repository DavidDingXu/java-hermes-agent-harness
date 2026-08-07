package com.dingxu.ai.hermes.cli;

import com.dingxu.ai.hermes.core.AgentEvent;
import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentState;
import com.dingxu.ai.hermes.core.FinishReason;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaHermesCliTest {

    @Test
    void runsPromptThroughRuntimeAndPrintsFinalAnswer() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<Integer> turns = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JavaHermesCli cli = new JavaHermesCli(request -> {
            prompt.set(request.userMessage());
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

        int exitCode = cli.run("--prompt", "inspect project", "--max-turns", "4");

        assertEquals(0, exitCode);
        assertEquals("inspect project", prompt.get());
        assertEquals(4, turns.get());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("project inspected"));
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

    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }
}
