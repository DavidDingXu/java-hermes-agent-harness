package com.dingxu.ai.hermes.scheduler;

public record DeliveryTarget(String kind, String destination) {

    public DeliveryTarget {
        kind = kind == null ? "" : kind.trim();
        destination = destination == null ? "" : destination.trim();
        if (kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
    }

    public static DeliveryTarget local(String destination) {
        return new DeliveryTarget("local", destination);
    }
}
