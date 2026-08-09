package com.ading.ai.hermes.gateway.feishu;

public record FeishuReply(String chatId, String text) {

    public FeishuReply {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
        text = text == null ? "" : text;
    }
}
