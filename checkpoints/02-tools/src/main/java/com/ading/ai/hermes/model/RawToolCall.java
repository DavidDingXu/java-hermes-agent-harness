package com.ading.ai.hermes.model;

public record RawToolCall(String callId, String name, String argumentsJson) {

    public RawToolCall {
        callId = callId == null ? "" : callId;
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        argumentsJson = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
    }
}
