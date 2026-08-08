package com.ading.ai.hermes.control;

public record EmergencyStopState(String reason, String engagedAt) {

    public EmergencyStopState {
        reason = reason == null ? "" : reason.trim();
        engagedAt = engagedAt == null ? "" : engagedAt.trim();
    }
}
