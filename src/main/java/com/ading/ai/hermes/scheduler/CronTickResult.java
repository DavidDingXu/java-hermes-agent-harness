package com.ading.ai.hermes.scheduler;

import java.util.List;

public record CronTickResult(
        List<CronRunRecord> runs,
        List<CronDeliveryResult> deliveries,
        List<CronRunFailure> failures,
        String skippedReason
) {

    public CronTickResult {
        runs = runs == null ? List.of() : List.copyOf(runs);
        deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
        failures = failures == null ? List.of() : List.copyOf(failures);
        skippedReason = skippedReason == null ? "" : skippedReason.trim();
    }

    public CronTickResult(List<CronRunRecord> runs) {
        this(runs, List.of(), List.of(), "");
    }

    public CronTickResult(List<CronRunRecord> runs, String skippedReason) {
        this(runs, List.of(), List.of(), skippedReason);
    }

    public boolean skipped() {
        return !skippedReason.isBlank();
    }
}
