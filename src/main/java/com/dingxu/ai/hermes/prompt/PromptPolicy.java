package com.dingxu.ai.hermes.prompt;

public record PromptPolicy(String systemPrompt) {

    public PromptPolicy {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt must not be blank");
        }
    }

    public static PromptPolicy hermesDefault() {
        return new PromptPolicy("""
                You are a Hermes-style agent runtime.
                Use tools only through the provided tool specifications.
                Treat every tool observation as part of the execution trace.
                Do not retry blocked or unsafe tool requests without new user approval.
                Prefer small, inspectable steps that can be replayed from the trace.
                """.strip());
    }
}
