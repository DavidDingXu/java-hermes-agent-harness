package com.ading.ai.hermes.scheduler;

import java.time.Instant;
import java.util.Objects;

public record CronJob(
        String id,
        String name,
        String prompt,
        CronSchedule schedule,
        Instant nextRunAt,
        DeliveryTarget deliveryTarget,
        boolean paused
) {

    public CronJob {
        id = requireText(id, "id");
        name = requireText(name, "name");
        prompt = requireText(prompt, "prompt");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(nextRunAt, "nextRunAt must not be null");
        Objects.requireNonNull(deliveryTarget, "deliveryTarget must not be null");
    }

    public boolean dueAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !paused && !nextRunAt.isAfter(now);
    }

    public String fireKey() {
        return id + "@" + nextRunAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
