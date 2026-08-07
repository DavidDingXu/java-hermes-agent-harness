package com.dingxu.ai.hermes.verification;

import com.dingxu.ai.hermes.core.AgentRunResult;

@FunctionalInterface
public interface CompletionVerifier {

    CompletionEvidence verify(AgentRunResult result);
}
