package com.ading.ai.hermes.metrics;

@FunctionalInterface
public interface ModelMetrics {

    void record(ModelCallMetric metric);
}
