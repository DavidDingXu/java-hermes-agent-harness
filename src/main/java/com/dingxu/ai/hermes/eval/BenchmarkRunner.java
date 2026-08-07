package com.dingxu.ai.hermes.eval;

import com.dingxu.ai.hermes.core.AgentRunRequest;
import com.dingxu.ai.hermes.core.AgentRuntime;
import com.dingxu.ai.hermes.core.IterationBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BenchmarkRunner {

    private final AgentRuntime runtime;

    public BenchmarkRunner(AgentRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    public BenchmarkReport run(List<BenchmarkCase> cases) {
        Objects.requireNonNull(cases, "cases must not be null");
        List<BenchmarkCaseResult> results = new ArrayList<>();
        for (BenchmarkCase benchmarkCase : List.copyOf(cases)) {
            results.add(runCase(benchmarkCase));
        }
        return new BenchmarkReport(results);
    }

    private BenchmarkCaseResult runCase(BenchmarkCase benchmarkCase) {
        try {
            var runResult = runtime.run(AgentRunRequest.start(
                    benchmarkCase.prompt(),
                    IterationBudget.maxTurns(benchmarkCase.maxTurns())
            ));
            BenchmarkEvidence evidence = benchmarkCase.evaluator().evaluate(runResult);
            int score = evidence.passed() ? Math.min(evidence.score(), benchmarkCase.maxScore()) : 0;
            return new BenchmarkCaseResult(
                    benchmarkCase.id(),
                    evidence.passed(),
                    score,
                    benchmarkCase.maxScore(),
                    evidence.detail()
            );
        } catch (RuntimeException error) {
            return new BenchmarkCaseResult(
                    benchmarkCase.id(),
                    false,
                    0,
                    benchmarkCase.maxScore(),
                    "execution failed: " + error.getClass().getSimpleName()
            );
        }
    }
}
