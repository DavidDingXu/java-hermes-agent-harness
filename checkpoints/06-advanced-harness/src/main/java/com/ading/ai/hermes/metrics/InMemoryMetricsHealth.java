package com.ading.ai.hermes.metrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryMetricsHealth implements MetricsFailureHandler {

    private final AtomicInteger droppedCount = new AtomicInteger();
    private final AtomicReference<String> latestError = new AtomicReference<>("");

    @Override
    public void onDropped(ModelCallMetric metric, RuntimeException error) {
        droppedCount.incrementAndGet();
        latestError.set(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    public int droppedCount() {
        return droppedCount.get();
    }

    public String latestError() {
        return latestError.get();
    }
}
