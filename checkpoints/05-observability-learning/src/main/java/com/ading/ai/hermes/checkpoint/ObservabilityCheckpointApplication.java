package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.context.reference.ContextReferenceResolver;
import com.ading.ai.hermes.context.reference.ContextReferenceResult;
import com.ading.ai.hermes.context.reference.UrlContextFetcher;
import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.eval.BenchmarkCase;
import com.ading.ai.hermes.eval.BenchmarkEvidence;
import com.ading.ai.hermes.eval.BenchmarkReport;
import com.ading.ai.hermes.eval.BenchmarkRunner;
import com.ading.ai.hermes.memory.MemoryPolicy;
import com.ading.ai.hermes.memory.MemoryStore;
import com.ading.ai.hermes.metrics.InMemoryModelMetrics;
import com.ading.ai.hermes.metrics.MeteredModelProvider;
import com.ading.ai.hermes.model.ModelProvider;
import com.ading.ai.hermes.model.ToolSpec;
import com.ading.ai.hermes.observability.TraceRedactor;
import com.ading.ai.hermes.observability.TrajectoryRecord;
import com.ading.ai.hermes.observability.TrajectoryRecorder;
import com.ading.ai.hermes.skill.SelfImprovementLoop;
import com.ading.ai.hermes.skill.SelfImprovementResult;
import com.ading.ai.hermes.skill.SkillApprovalFlow;
import com.ading.ai.hermes.skill.SkillCandidateGenerator;
import com.ading.ai.hermes.skill.TrajectorySelfImprovementReviewer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ObservabilityCheckpointApplication {

    private ObservabilityCheckpointApplication() {
    }

    public static void main(String[] args) throws Exception {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        Path stateDirectory = Files.createTempDirectory("hermes-observability-");
        try {
            ContextReferenceResult reference = resolveContextReference(stateDirectory);
            OnlineBenchmark onlineBenchmark = runMeasuredBenchmark(readerModel, reference);
            InMemoryModelMetrics metrics = onlineBenchmark.metrics();
            AgentRunResult runResult = onlineBenchmark.result();
            TrajectoryRecord trajectory = recordTrajectory(runResult);
            BenchmarkReport benchmark = onlineBenchmark.report();
            SelfImprovementResult improvement = reviewForImprovement(stateDirectory, trajectory);

            require(!metrics.calls().isEmpty(), "真实模型指标没有记录");
            require(runResult.state().events().stream().anyMatch(event ->
                    event.kind() == com.ading.ai.hermes.core.AgentEventKind.ERROR_RECOVERED),
                    "受控 Provider 故障没有进入恢复轨迹");
            require(runResult.state().events().stream().anyMatch(event ->
                    event.kind() == com.ading.ai.hermes.core.AgentEventKind.TOOL_OBSERVED
                            && event.toolObservation().success()), "真实模型没有完成工具闭环");
            require(runResult.state().events().stream().filter(event ->
                    event.kind() == com.ading.ai.hermes.core.AgentEventKind.TOOL_REQUESTED).count() == 1,
                    "真实模型重复调用了阶段检查工具");
            require(runResult.finalAnswer().contains("Maven")
                            && runResult.finalAnswer().contains("Trajectory")
                            && runResult.finalAnswer().contains("Metrics"),
                    "真实模型没有使用工具证据完成观测说明");
            verifyStructuredRedaction();
            require(benchmark.passedCases() == 1, "Benchmark 没有通过");
            require(improvement.memoryWrites().stream().anyMatch(write -> write.written()), "Memory 候选没有落盘");
            require(improvement.pendingSkills().size() == 1, "改进候选没有进入人工审批队列");
            require(reference.attachedContext().contains("Hermes 运行时"), "文件引用没有进入当前轮");

            System.out.println("[阶段 05] 可观测与受控学习运行成功");
            System.out.println("Model Metrics: " + metrics.calls().size() + " 次调用");
            System.out.println("Trajectory Events: " + trajectory.events().size() + " 条（敏感值已脱敏）");
            System.out.println("Benchmark: " + benchmark.score() + "/" + benchmark.maxScore());
            System.out.println("待审批 Skill: " + improvement.pendingSkills().getFirst().id());
            System.out.println("上下文引用: " + reference.references().getFirst().raw());
            System.out.println("真实模型: " + readerModel.model());
            System.out.println("在线指标: " + metrics.calls().stream()
                    .map(call -> call.outcome().name())
                    .distinct()
                    .toList());
            System.out.println("在线回答: " + ReaderModelRuntime.preview(runResult.finalAnswer()));
        } finally {
            deleteRecursively(stateDirectory);
        }
    }

    private static OnlineBenchmark runMeasuredBenchmark(
            ReaderModelRuntime readerModel,
            ContextReferenceResult reference
    ) {
        InMemoryModelMetrics metrics = new InMemoryModelMetrics();
        MeteredModelProvider meteredProvider = new MeteredModelProvider(
                readerModel.provider(),
                metrics
        );
        AtomicInteger providerAttempts = new AtomicInteger();
        ModelProvider failOnceThenUseRealModel = request -> {
            if (providerAttempts.getAndIncrement() == 0) {
                throw new IllegalStateException("controlled provider failure for recovery evidence");
            }
            return meteredProvider.complete(request);
        };
        ToolSpec inspectRuntime = new ToolSpec(
                "inspect_runtime",
                "只在第一轮调用一次，读取当前阶段的运行时标记；看到结果后禁止重复调用。",
                Map.of()
        );
        AtomicReference<AgentRunResult> captured = new AtomicReference<>();
        var measuredRuntime = (com.ading.ai.hermes.core.AgentRuntime) request -> {
            AgentRunResult result = readerModel.runRecovering(
                    failOnceThenUseRealModel,
                    """
                            你正在验证 Hermes 的观测、评测与学习链路。
                            第一轮必须且只能调用一次 inspect_runtime。
                            一旦上下文已经有 inspect_runtime 的 Observation，立即停止调用工具并给出最终回答。
                            最终回答必须同时使用文件引用与 Observation 中的 Maven 事实；
                            再用简洁中文说明 Trajectory 与 Metrics 的区别。
                            """,
                    request.userMessage(),
                    toolRequest -> ToolObservation.success(
                            toolRequest.callId(),
                            "当前项目使用 Maven；这是 inspect_runtime 返回的 Observation。"
                    ),
                    List.of(inspectRuntime),
                    request.budget().maxTurns()
            );
            captured.set(result);
            return result;
        };
        BenchmarkCase benchmarkCase = new BenchmarkCase(
                "complete-observed-task",
                reference.resolvedMessage()
                        + "\n希望你先给结论，再解释原因。请检查当前运行时，并说明 Trajectory 与 Metrics 的区别。",
                6,
                10,
                result -> result.finishReason() == FinishReason.FINAL_ANSWER
                        && result.state().events().stream().anyMatch(event ->
                        event.kind() == com.ading.ai.hermes.core.AgentEventKind.TOOL_OBSERVED)
                        ? BenchmarkEvidence.pass(10, "真实模型完成工具闭环并给出最终回答")
                        : BenchmarkEvidence.fail("真实运行没有完成工具闭环")
        );
        BenchmarkReport report = new BenchmarkRunner(measuredRuntime).run(List.of(benchmarkCase));
        AgentRunResult result = captured.get();
        require(result != null, "Benchmark 没有调用真实 Runtime");
        require(result.finishReason() == FinishReason.FINAL_ANSWER, "真实模型没有正常完成观测任务");
        return new OnlineBenchmark(metrics, result, report);
    }

    private static TrajectoryRecord recordTrajectory(AgentRunResult runResult) {
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T08:00:00Z"), ZoneOffset.UTC);
        return new TrajectoryRecorder(clock, new TraceRedactor())
                .recordRun("reader-session", "turn-1", runResult);
    }

    private static void verifyStructuredRedaction() {
        Map<String, Object> redacted = new TraceRedactor().redactMap(Map.of(
                "apiKey", "ordinary-looking-secret",
                "headers", Map.of("Authorization", "Bearer reader-secret")
        ));
        require("[REDACTED]".equals(redacted.get("apiKey")), "顶层敏感字段没有脱敏");
        require("[REDACTED]".equals(((Map<?, ?>) redacted.get("headers")).get("Authorization")),
                "嵌套敏感字段没有脱敏");
    }

    private static SelfImprovementResult reviewForImprovement(
            Path stateDirectory,
            TrajectoryRecord trajectory
    ) {
        MemoryStore memories = new MemoryStore(
                MemoryPolicy.defaultPolicy(),
                4_096,
                4_096,
                stateDirectory.resolve("memory")
        );
        SkillApprovalFlow approvals = new SkillApprovalFlow(stateDirectory.resolve("skills"));
        SelfImprovementLoop improvementLoop = new SelfImprovementLoop(
                new TrajectorySelfImprovementReviewer(new SkillCandidateGenerator()),
                memories,
                approvals
        );
        SelfImprovementResult result = improvementLoop.review(trajectory);
        require(approvals.approvedSkills().isEmpty(), "Skill 不应绕过人工审批直接生效");
        return result;
    }

    private static ContextReferenceResult resolveContextReference(Path workspace) throws Exception {
        Files.writeString(
                workspace.resolve("README.md"),
                "Hermes 运行时以证据驱动当前轮。\n",
                StandardCharsets.UTF_8
        );
        ContextReferenceResolver resolver = new ContextReferenceResolver(
                workspace,
                4_096,
                UrlContextFetcher.disabled(),
                reference -> ""
        );
        return resolver.resolve("请检查 @file:README.md:1");
    }

    private static void deleteRecursively(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record OnlineBenchmark(
            InMemoryModelMetrics metrics,
            AgentRunResult result,
            BenchmarkReport report
    ) {
    }
}
