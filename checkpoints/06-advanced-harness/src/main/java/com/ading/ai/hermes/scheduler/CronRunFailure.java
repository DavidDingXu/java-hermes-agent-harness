package com.ading.ai.hermes.scheduler;

public record CronRunFailure(
        String fireKey,
        String jobId,
        CronFailureStage stage,
        String error
) {

    public CronRunFailure {
        fireKey = requireText(fireKey, "fireKey");
        jobId = requireText(jobId, "jobId");
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        error = error == null ? "" : error.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
