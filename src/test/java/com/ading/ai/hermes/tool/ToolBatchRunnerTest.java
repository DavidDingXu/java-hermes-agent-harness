package com.ading.ai.hermes.tool;

import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolBatchRunnerTest {

    @Test
    void returnsObservationsInRequestOrder() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "echo",
                        "Return text",
                        ToolSchema.object().requiredString("text"),
                        request -> ToolResult.success(request.callId(), request.arguments().get("text").toString())
                ));

        ToolBatchRunner runner = new ToolBatchRunner(registry, 2);
        List<ToolObservation> observations = runner.execute(List.of(
                new ToolRequest("call-1", "echo", Map.of("text", "first")),
                new ToolRequest("call-2", "echo", Map.of("text", "second"))
        ));

        assertEquals(List.of(
                ToolObservation.success("call-1", "first"),
                ToolObservation.success("call-2", "second")
        ), observations);
    }

    @Test
    void runsIndependentToolCallsConcurrently() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "wait",
                        "Wait until released",
                        ToolSchema.object().requiredString("text"),
                        request -> {
                            bothStarted.countDown();
                            await(release);
                            return ToolResult.success(request.callId(), request.arguments().get("text").toString());
                        }
                ));

        ToolBatchRunner runner = new ToolBatchRunner(registry, 2);
        Thread caller = new Thread(() -> runner.execute(List.of(
                new ToolRequest("call-1", "wait", Map.of("text", "first")),
                new ToolRequest("call-2", "wait", Map.of("text", "second"))
        )));
        caller.start();

        assertTrue(await(bothStarted), "both tool calls should start before release");
        release.countDown();
        awaitThread(caller);
    }

    @Test
    void convertsToolRuntimeExceptionToFailureObservation() {
        ToolRegistry registry = ToolRegistry.empty()
                .register(new ToolDefinition(
                        "explode",
                        "Throw an exception",
                        ToolSchema.object(),
                        request -> {
                            throw new IllegalStateException("boom");
                        }
                ));

        ToolBatchRunner runner = new ToolBatchRunner(registry, 2);
        List<ToolObservation> observations = runner.execute(List.of(
                new ToolRequest("call-1", "explode", Map.of())
        ));

        assertEquals(List.of(
                ToolObservation.executionFailure("call-1", "tool execution failed: boom")
        ), observations);
        assertEquals(
                com.ading.ai.hermes.core.ToolFailureKind.EXECUTION_ERROR,
                observations.getFirst().failureKind()
        );
    }

    @Test
    void runsWholeBatchSequentiallyWhenOneRequestIsNotParallelSafe() {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ToolBatchRunner runner = new ToolBatchRunner(request -> {
            if (request.callId().equals("call-1")) {
                firstStarted.countDown();
                await(releaseFirst);
            } else {
                secondStarted.countDown();
            }
            return ToolObservation.success(request.callId(), request.name());
        }, 2, request -> !request.name().equals("edit_file"));

        Thread caller = new Thread(() -> runner.execute(List.of(
                new ToolRequest("call-1", "read_file", Map.of()),
                new ToolRequest("call-2", "edit_file", Map.of())
        )));
        caller.start();

        assertTrue(await(firstStarted));
        assertFalse(await(secondStarted, 150), "unsafe batch must not overlap tool execution");
        releaseFirst.countDown();
        awaitThread(caller);
        assertTrue(await(secondStarted));
    }

    @Test
    void returnsEmptyListForEmptyBatch() {
        AtomicInteger calls = new AtomicInteger();
        ToolBatchRunner runner = new ToolBatchRunner(request -> {
            calls.incrementAndGet();
            return ToolObservation.success(request.callId(), "unused");
        }, 2);
        assertEquals(List.of(), runner.execute(List.of()));
        assertEquals(0, calls.get());
    }

    private boolean await(CountDownLatch latch) {
        return await(latch, 2_000);
    }

    private boolean await(CountDownLatch latch, long timeoutMillis) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void awaitThread(Thread thread) {
        try {
            thread.join(2000);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        assertFalse(thread.isAlive(), "tool batch caller should finish");
    }
}
