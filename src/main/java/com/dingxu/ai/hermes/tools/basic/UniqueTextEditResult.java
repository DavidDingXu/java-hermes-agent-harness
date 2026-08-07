package com.dingxu.ai.hermes.tools.basic;

public record UniqueTextEditResult(boolean success, String content, String error) {

    public UniqueTextEditResult {
        content = content == null ? "" : content;
        error = error == null ? "" : error;
    }

    public static UniqueTextEditResult success(String content) {
        return new UniqueTextEditResult(true, content, "");
    }

    public static UniqueTextEditResult failure(String content, String error) {
        return new UniqueTextEditResult(false, content, error);
    }
}
