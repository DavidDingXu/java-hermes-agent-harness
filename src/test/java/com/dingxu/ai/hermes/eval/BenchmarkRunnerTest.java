package com.dingxu.ai.hermes.eval;

import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentState;
import com.dingxu.ai.hermes.core.FinishReason;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunnerTest {

    @Test
    void runsCasesThroughRuntimeAndAggregatesScoreAndEvidence() {
        BenchmarkRunner runner = new BenchmarkRunner(request -> new AgentRunResult(
                FinishReason.FINAL_ANSWER,
                request.userMessage().contains("pass") ? "verified" : "incomplete",
                AgentState.start(request.userMessage())
        ));
        List<BenchmarkCase> cases = List.of(
                new BenchmarkCase("passing", "pass this task", 3, 5,
                        result -> BenchmarkEvidence.pass(5, "returned verified answer")),
                new BenchmarkCase("failing", "fail this task", 2, 5,
                        result -> BenchmarkEvidence.fail("missing verification evidence"))
        );

        BenchmarkReport report = runner.run(cases);

        assertEquals(2, report.totalCases());
        assertEquals(1, report.passedCases());
        assertEquals(5, report.score());
        assertEquals(10, report.maxScore());
        assertTrue(report.results().get(0).passed());
        assertFalse(report.results().get(1).passed());
        assertEquals("missing verification evidence", report.results().get(1).evidence());
    }
}
