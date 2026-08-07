package com.ading.ai.hermes.scheduler;

import java.time.Duration;
import java.time.Instant;

public record CronSchedule(Duration interval) {

    public CronSchedule {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
    }

    public static CronSchedule everyMinutes(long minutes) {
        return new CronSchedule(Duration.ofMinutes(minutes));
    }

    public Instant nextAfter(Instant fireTime) {
        return fireTime.plus(interval);
    }
}
