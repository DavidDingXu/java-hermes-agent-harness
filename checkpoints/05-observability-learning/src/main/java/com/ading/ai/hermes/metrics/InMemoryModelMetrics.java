package com.ading.ai.hermes.metrics;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryModelMetrics implements ModelMetrics {

    private final List<ModelCallMetric> calls = new ArrayList<>();

    @Override
    public synchronized void record(ModelCallMetric metric) {
        calls.add(metric);
    }

    public synchronized List<ModelCallMetric> calls() {
        return List.copyOf(calls);
    }
}
