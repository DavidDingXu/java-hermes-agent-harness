package com.dingxu.ai.hermes.tool;

public record ToolArgumentValidation(boolean valid, String message) {

    public ToolArgumentValidation {
        message = message == null ? "" : message;
    }

    public static ToolArgumentValidation ok() {
        return new ToolArgumentValidation(true, "");
    }

    public static ToolArgumentValidation invalid(String message) {
        return new ToolArgumentValidation(false, message);
    }
}
