package com.dingxu.ai.hermes.metrics;

import com.dingxu.ai.hermes.model.Usage;
import java.time.Duration;
import java.util.Objects;

public record ModelCallMetric(
        String provider,
        Usage usage,
        Duration duration,
        ModelCallOutcome outcome,
        String errorType
) {

    public ModelCallMetric {
        provider = provider == null || provider.isBlank() ? "unknown" : provider.trim();
        usage = usage == null ? Usage.empty() : usage;
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        errorType = errorType == null ? "" : errorType;
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    public static ModelCallMetric success(String provider, Usage usage, Duration duration) {
        return new ModelCallMetric(provider, usage, duration, ModelCallOutcome.SUCCESS, "");
    }

    public static ModelCallMetric failure(Duration duration, RuntimeException error) {
        return new ModelCallMetric(
                "unknown",
                Usage.empty(),
                duration,
                ModelCallOutcome.FAILURE,
                error.getClass().getSimpleName()
        );
    }
}
