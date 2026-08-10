package com.ading.ai.hermes.metrics;

@FunctionalInterface
public interface MetricsFailureHandler {

    void onDropped(ModelCallMetric metric, RuntimeException error);

    static MetricsFailureHandler ignore() {
        return (metric, error) -> { };
    }
}
