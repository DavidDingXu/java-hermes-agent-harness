package com.ading.ai.hermes.security;

import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuardedToolDriverTest {

    @Test
    void executesToolWhenPolicyAllowsRequest() {
        GuardedToolDriver driver = new GuardedToolDriver(
                request -> ToolObservation.success(request.callId(), "executed"),
                List.of(ToolPolicy.allowAll())
        );

        ToolObservation observation = driver.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))
        );

        assertEquals(ToolObservation.success("call-1", "executed"), observation);
    }

    @Test
    void blocksToolNameBeforeDelegateRuns() {
        AtomicInteger delegateCalls = new AtomicInteger();
        GuardedToolDriver driver = new GuardedToolDriver(
                request -> {
                    delegateCalls.incrementAndGet();
                    return ToolObservation.success(request.callId(), "should not run");
                },
                List.of(ToolPolicy.blockTool("run_shell", "shell execution requires approval"))
        );

        ToolObservation observation = driver.execute(
                new ToolRequest("call-1", "run_shell", Map.of("command", "rm -rf target"))
        );

        assertEquals(0, delegateCalls.get());
        assertEquals(ToolObservation.failure(
                "call-1",
                "tool request blocked: shell execution requires approval"
        ), observation);
    }

    @Test
    void blocksArgumentPatternBeforeDelegateRuns() {
        AtomicInteger delegateCalls = new AtomicInteger();
        GuardedToolDriver driver = new GuardedToolDriver(
                request -> {
                    delegateCalls.incrementAndGet();
                    return ToolObservation.success(request.callId(), "should not run");
                },
                List.of(ToolPolicy.blockArgumentContaining("path", "../", "path traversal requires review"))
        );

        ToolObservation observation = driver.execute(
                new ToolRequest("call-1", "read_file", Map.of("path", "../secret.txt"))
        );

        assertEquals(0, delegateCalls.get());
        assertEquals(ToolObservation.failure(
                "call-1",
                "tool request blocked: path traversal requires review"
        ), observation);
    }

    @Test
    void stopsAtFirstBlockingPolicy() {
        AtomicInteger secondPolicyCalls = new AtomicInteger();
        ToolPolicy first = ToolPolicy.blockTool("run_shell", "first block");
        ToolPolicy second = request -> {
            secondPolicyCalls.incrementAndGet();
            return ToolDecision.allow();
        };
        GuardedToolDriver driver = new GuardedToolDriver(
                request -> ToolObservation.success(request.callId(), "should not run"),
                List.of(first, second)
        );

        ToolObservation observation = driver.execute(
                new ToolRequest("call-1", "run_shell", Map.of("command", "echo hi"))
        );

        assertEquals(0, secondPolicyCalls.get());
        assertEquals("tool request blocked: first block", observation.content());
    }
}
