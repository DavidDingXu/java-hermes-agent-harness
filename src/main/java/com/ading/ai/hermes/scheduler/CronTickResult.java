package com.ading.ai.hermes.scheduler;

import java.util.List;

public record CronTickResult(List<CronRunRecord> runs) {

    public CronTickResult {
        runs = runs == null ? List.of() : List.copyOf(runs);
    }
}
