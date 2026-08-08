package com.ading.ai.hermes.scheduler;

import java.util.List;

public record CronTickResult(List<CronRunRecord> runs, String skippedReason) {

    public CronTickResult {
        runs = runs == null ? List.of() : List.copyOf(runs);
        skippedReason = skippedReason == null ? "" : skippedReason.trim();
    }

    public CronTickResult(List<CronRunRecord> runs) {
        this(runs, "");
    }

    public boolean skipped() {
        return !skippedReason.isBlank();
    }
}
