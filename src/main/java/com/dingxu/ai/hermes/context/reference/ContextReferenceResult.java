package com.dingxu.ai.hermes.context.reference;

import java.util.List;

public record ContextReferenceResult(
        String originalMessage,
        String resolvedMessage,
        String attachedContext,
        List<ContextReference> references,
        List<String> warnings,
        boolean blocked
) {
    public ContextReferenceResult {
        references = List.copyOf(references);
        warnings = List.copyOf(warnings);
    }
}
