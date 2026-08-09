package com.ading.ai.hermes.eval;

import com.ading.ai.hermes.core.AgentRunResult;

@FunctionalInterface
public interface BenchmarkEvaluator {

    BenchmarkEvidence evaluate(AgentRunResult result);
}
