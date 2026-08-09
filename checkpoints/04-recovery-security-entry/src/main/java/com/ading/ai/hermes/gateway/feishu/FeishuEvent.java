package com.ading.ai.hermes.gateway.feishu;

import java.util.Objects;

public record FeishuEvent(
        FeishuEventKind kind,
        String eventId,
        String challenge,
        String chatType,
        String chatId,
        String senderId,
        String text
) {

    public FeishuEvent {
        Objects.requireNonNull(kind, "kind must not be null");
        eventId = normalize(eventId);
        challenge = normalize(challenge);
        chatType = normalize(chatType);
        chatId = normalize(chatId);
        senderId = normalize(senderId);
        text = text == null ? "" : text;
    }

    public static FeishuEvent challenge(String challenge) {
        return new FeishuEvent(FeishuEventKind.CHALLENGE, "", challenge, "", "", "", "");
    }

    public static FeishuEvent text(String eventId, String chatId, String senderId, String text) {
        return text(eventId, "chat", chatId, senderId, text);
    }

    public static FeishuEvent text(
            String eventId,
            String chatType,
            String chatId,
            String senderId,
            String text
    ) {
        return new FeishuEvent(
                FeishuEventKind.TEXT_MESSAGE,
                eventId,
                "",
                chatType,
                chatId,
                senderId,
                text
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
