package com.dingxu.ai.hermes.eval;

import com.dingxu.ai.hermes.core.AgentRunResult;

@FunctionalInterface
public interface BenchmarkEvaluator {

    BenchmarkEvidence evaluate(AgentRunResult result);
}
