package com.ading.ai.hermes.scheduler;

import java.util.Objects;

public record CronDeliveryResult(
        String runId,
        DeliveryTarget target,
        CronDeliveryStatus status,
        String error
) {

    public CronDeliveryResult {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        runId = runId.trim();
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(status, "status must not be null");
        error = error == null ? "" : error.trim();
    }

    public static CronDeliveryResult delivered(CronRunRecord run) {
        return new CronDeliveryResult(
                run.runId(), run.deliveryTarget(), CronDeliveryStatus.DELIVERED, ""
        );
    }

    public static CronDeliveryResult failed(CronRunRecord run, RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new CronDeliveryResult(
                run.runId(), run.deliveryTarget(), CronDeliveryStatus.FAILED, message
        );
    }
}
