package com.ading.ai.hermes.metrics;

import com.ading.ai.hermes.model.ChatRequest;
import com.ading.ai.hermes.model.ChatResponse;
import com.ading.ai.hermes.model.ModelProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class MeteredModelProvider implements ModelProvider {

    private final ModelProvider delegate;
    private final ModelMetrics metrics;
    private final LongSupplier nanoTime;
    private final MetricsFailureHandler failureHandler;

    public MeteredModelProvider(ModelProvider delegate, ModelMetrics metrics) {
        this(delegate, metrics, System::nanoTime);
    }

    public MeteredModelProvider(ModelProvider delegate, ModelMetrics metrics, LongSupplier nanoTime) {
        this(delegate, metrics, nanoTime, MetricsFailureHandler.ignore());
    }

    public MeteredModelProvider(
            ModelProvider delegate,
            ModelMetrics metrics,
            LongSupplier nanoTime,
            MetricsFailureHandler failureHandler
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler must not be null");
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        long started = nanoTime.getAsLong();
        ChatResponse response;
        try {
            response = delegate.complete(request);
        } catch (RuntimeException error) {
            recordSafely(ModelCallMetric.failure(elapsed(started), error));
            throw error;
        }
        recordSafely(ModelCallMetric.success(
                response.provider(),
                response.usage(),
                elapsed(started)
        ));
        return response;
    }

    private void recordSafely(ModelCallMetric metric) {
        try {
            metrics.record(metric);
        } catch (RuntimeException error) {
            try {
                failureHandler.onDropped(metric, error);
            } catch (RuntimeException ignored) {
                // Metrics must never change the result of the model call they observe.
            }
        }
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - started));
    }
}
