package com.dingxu.ai.hermes.gateway.feishu;

import java.util.Objects;

public record FeishuHandleResult(FeishuHandleStatus status, String responseBody, String error) {

    public FeishuHandleResult {
        Objects.requireNonNull(status, "status must not be null");
        responseBody = responseBody == null ? "" : responseBody;
        error = error == null ? "" : error;
    }

    public static FeishuHandleResult challenge(String challenge) {
        return new FeishuHandleResult(FeishuHandleStatus.CHALLENGE, challenge, "");
    }

    public static FeishuHandleResult processed() {
        return new FeishuHandleResult(FeishuHandleStatus.PROCESSED, "", "");
    }

    public static FeishuHandleResult duplicate() {
        return new FeishuHandleResult(FeishuHandleStatus.DUPLICATE, "", "");
    }

    public static FeishuHandleResult rejected(String error) {
        return new FeishuHandleResult(FeishuHandleStatus.REJECTED, "", error);
    }
}
