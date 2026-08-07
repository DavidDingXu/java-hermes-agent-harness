package com.dingxu.ai.hermes.metrics;

@FunctionalInterface
public interface ModelMetrics {

    void record(ModelCallMetric metric);
}
