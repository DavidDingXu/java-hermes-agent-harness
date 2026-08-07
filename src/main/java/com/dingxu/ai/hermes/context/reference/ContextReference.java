package com.dingxu.ai.hermes.context.reference;

public record ContextReference(
        String raw,
        ContextReferenceKind kind,
        String target,
        Integer lineStart,
        Integer lineEnd
) {
}
