package com.ading.ai.hermes.gateway;

public record GatewayIdentity(String platform, String chatType, String chatId, String userId) {

    public GatewayIdentity {
        platform = requireText(platform, "platform");
        chatType = requireText(chatType, "chatType");
        chatId = requireText(chatId, "chatId");
        userId = requireText(userId, "userId");
    }

    public String principalKey() {
        return platform + ":" + userId;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must not contain control characters");
        }
        return normalized;
    }
}
