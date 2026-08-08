package com.ading.ai.hermes.control;

public record AdmissionDecision(boolean allowed, String reason) {

    public AdmissionDecision {
        reason = reason == null ? "" : reason.trim();
        if (!allowed && reason.isBlank()) {
            throw new IllegalArgumentException("rejected admission requires a reason");
        }
    }

    public static AdmissionDecision allow() {
        return new AdmissionDecision(true, "");
    }

    public static AdmissionDecision reject(String reason) {
        return new AdmissionDecision(false, reason);
    }
}
