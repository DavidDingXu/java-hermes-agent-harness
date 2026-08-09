package com.ading.ai.hermes.verification;

import com.ading.ai.hermes.core.AgentRunResult;

@FunctionalInterface
public interface CompletionVerifier {

    CompletionEvidence verify(AgentRunResult result);
}
