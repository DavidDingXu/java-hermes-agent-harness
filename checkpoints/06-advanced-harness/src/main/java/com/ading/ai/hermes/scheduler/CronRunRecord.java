package com.ading.ai.hermes.scheduler;

import com.ading.ai.hermes.core.FinishReason;

import java.time.Instant;
import java.util.Objects;

public record CronRunRecord(
        String runId,
        String jobId,
        String jobName,
        String fireKey,
        Instant firedAt,
        Instant nextRunAt,
        DeliveryTarget deliveryTarget,
        String finalAnswer,
        FinishReason finishReason
) {

    public CronRunRecord {
        runId = requireText(runId, "runId");
        jobId = requireText(jobId, "jobId");
        jobName = requireText(jobName, "jobName");
        fireKey = requireText(fireKey, "fireKey");
        Objects.requireNonNull(firedAt, "firedAt must not be null");
        Objects.requireNonNull(nextRunAt, "nextRunAt must not be null");
        Objects.requireNonNull(deliveryTarget, "deliveryTarget must not be null");
        finalAnswer = finalAnswer == null ? "" : finalAnswer;
        Objects.requireNonNull(finishReason, "finishReason must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
