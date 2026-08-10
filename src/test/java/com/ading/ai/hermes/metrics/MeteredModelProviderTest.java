package com.ading.ai.hermes.metrics;

import com.ading.ai.hermes.core.ModelTurn;
import com.ading.ai.hermes.model.ChatMessage;
import com.ading.ai.hermes.model.ChatRequest;
import com.ading.ai.hermes.model.ChatResponse;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.Usage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeteredModelProviderTest {

    @Test
    void recordsUsageProviderLatencyAndOutcomeWithoutChangingResponse() {
        ChatResponse response = new ChatResponse(ModelTurn.finalAnswer("done"), new Usage(12, 5), "test-provider");
        InMemoryModelMetrics metrics = new InMemoryModelMetrics();
        AtomicLong nanos = new AtomicLong();
        MeteredModelProvider provider = new MeteredModelProvider(
                request -> response,
                metrics,
                () -> nanos.getAndAdd(Duration.ofMillis(25).toNanos())
        );

        ChatResponse actual = provider.complete(request());

        assertSame(response, actual);
        assertEquals(1, metrics.calls().size());
        ModelCallMetric metric = metrics.calls().get(0);
        assertEquals("test-provider", metric.provider());
        assertEquals(new Usage(12, 5), metric.usage());
        assertEquals(Duration.ofMillis(25), metric.duration());
        assertEquals(ModelCallOutcome.SUCCESS, metric.outcome());
    }

    @Test
    void recordsFailedCallAndRethrowsOriginalError() {
        InMemoryModelMetrics metrics = new InMemoryModelMetrics();
        IllegalStateException failure = new IllegalStateException("provider unavailable");
        MeteredModelProvider provider = new MeteredModelProvider(request -> {
            throw failure;
        }, metrics, () -> 0L);

        IllegalStateException actual = assertThrows(IllegalStateException.class, () -> provider.complete(request()));

        assertSame(failure, actual);
        assertEquals(ModelCallOutcome.FAILURE, metrics.calls().get(0).outcome());
        assertEquals("IllegalStateException", metrics.calls().get(0).errorType());
    }

    @Test
    void returnsSuccessfulResponseWhenTheMetricsSinkFails() {
        ChatResponse response = new ChatResponse(ModelTurn.finalAnswer("done"), Usage.empty(), "test-provider");
        InMemoryMetricsHealth health = new InMemoryMetricsHealth();
        MeteredModelProvider provider = new MeteredModelProvider(
                request -> response,
                metric -> {
                    throw new IllegalStateException("metrics unavailable");
                },
                () -> 0L,
                health
        );

        ChatResponse actual = provider.complete(request());

        assertSame(response, actual);
        assertEquals(1, health.droppedCount());
        assertEquals("metrics unavailable", health.latestError());
    }

    @Test
    void preservesProviderFailureWhenRecordingTheFailureMetricAlsoFails() {
        IllegalStateException providerFailure = new IllegalStateException("provider unavailable");
        InMemoryMetricsHealth health = new InMemoryMetricsHealth();
        MeteredModelProvider provider = new MeteredModelProvider(
                request -> {
                    throw providerFailure;
                },
                metric -> {
                    throw new IllegalArgumentException("metrics unavailable");
                },
                () -> 0L,
                health
        );

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> provider.complete(request())
        );

        assertSame(providerFailure, actual);
        assertEquals(1, health.droppedCount());
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(ChatMessage.user("hello")),
                List.of(),
                new ModelOptions("fake", 0.0)
        );
    }
}
