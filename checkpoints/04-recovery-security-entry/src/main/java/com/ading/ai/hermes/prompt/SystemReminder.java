package com.ading.ai.hermes.prompt;

public record SystemReminder(String code, String text) {

    public SystemReminder {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("reminder code must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("reminder text must not be blank");
        }
        code = code.trim();
        text = text.trim();
    }
}
