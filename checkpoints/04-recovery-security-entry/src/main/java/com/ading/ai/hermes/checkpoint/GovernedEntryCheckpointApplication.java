package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.ErrorRecoveryPolicy;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.InterruptibleAgentLoop;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.ModelTurn;
import com.ading.ai.hermes.core.StopSignal;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.delegate.DelegationPolicy;
import com.ading.ai.hermes.delegate.DelegationRequest;
import com.ading.ai.hermes.delegate.DelegationResult;
import com.ading.ai.hermes.delegate.SubAgentRunner;
import com.ading.ai.hermes.delegate.SubAgentTask;
import com.ading.ai.hermes.gateway.GatewayTurnRequest;
import com.ading.ai.hermes.gateway.HttpGatewayHandler;
import com.ading.ai.hermes.gateway.HttpGatewayRequest;
import com.ading.ai.hermes.gateway.HttpGatewayResponse;
import com.ading.ai.hermes.scheduler.CronJob;
import com.ading.ai.hermes.scheduler.CronRunRecord;
import com.ading.ai.hermes.scheduler.CronSchedule;
import com.ading.ai.hermes.scheduler.CronScheduler;
import com.ading.ai.hermes.scheduler.CronTickResult;
import com.ading.ai.hermes.scheduler.DeliveryTarget;
import com.ading.ai.hermes.security.GuardedToolDriver;
import com.ading.ai.hermes.security.ToolPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class GovernedEntryCheckpointApplication {

    private GovernedEntryCheckpointApplication() {
    }

    public static void main(String[] args) {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        AgentRunResult recovered = runWithRecovery();
        ToolObservation blocked = verifyGuardrail();
        AgentRuntime runtime = request -> readerModel.runText(request.userMessage());
        HttpGatewayResponse gateway = invokeGateway(runtime);
        CronTickResult cron = invokeCron(runtime);
        DelegationResult delegation = invokeSubAgent(runtime);

        require(recovered.finishReason() == FinishReason.FINAL_ANSWER, "模型错误没有恢复");
        require(blocked.content().contains("blocked"), "Guardrail 没有拦截越界路径");
        require(gateway.ok(), "HTTP Gateway 没有复用 Runtime");
        require(cron.runs().size() == 1, "Cron 没有产生一次运行");
        require(delegation.results().size() == 1, "Subagent 没有返回隔离结果");

        System.out.println("[阶段 04] 可治理入口运行成功");
        System.out.println("错误恢复: " + recovered.finalAnswer());
        System.out.println("Guardrail: " + blocked.content());
        System.out.println("Gateway: HTTP " + gateway.status());
        System.out.println("Cron: " + cron.runs().getFirst().runId());
        System.out.println("Subagent: " + delegation.results().getFirst().status());
        System.out.println("真实模型: " + readerModel.model());
        System.out.println("在线 Gateway 回答: "
                + ReaderModelRuntime.preview(gateway.body().finalAnswer()));
    }

    private static AgentRunResult runWithRecovery() {
        AtomicInteger calls = new AtomicInteger();
        InterruptibleAgentLoop loop = new InterruptibleAgentLoop(
                state -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new IllegalStateException("provider temporarily unavailable");
                    }
                    return ModelTurn.finalAnswer("已依据错误证据恢复运行");
                },
                request -> ToolObservation.success(request.callId(), "ok"),
                StopSignal.none(),
                ErrorRecoveryPolicy.maxRecoveries(1)
        );
        AgentRunResult result = loop.run(AgentRunRequest.start(
                "完成一次可恢复运行",
                IterationBudget.maxTurns(3)
        ));
        boolean hasRecoveryEvidence = result.state().events().stream()
                .anyMatch(event -> event.kind() == AgentEventKind.ERROR_RECOVERED);
        require(hasRecoveryEvidence, "恢复过程没有写入事件流");
        return result;
    }

    private static ToolObservation verifyGuardrail() {
        AtomicInteger delegateCalls = new AtomicInteger();
        GuardedToolDriver guarded = new GuardedToolDriver(
                request -> {
                    delegateCalls.incrementAndGet();
                    return ToolObservation.success(request.callId(), "executed");
                },
                List.of(ToolPolicy.workspaceRelativePath("path"))
        );
        ToolObservation observation = guarded.execute(new ToolRequest(
                "guard-1",
                "read_file",
                Map.of("path", "../credentials.txt")
        ));
        require(delegateCalls.get() == 0, "被拒绝的工具仍然进入执行器");
        return observation;
    }

    private static HttpGatewayResponse invokeGateway(AgentRuntime runtime) {
        return new HttpGatewayHandler(runtime).handle(new HttpGatewayRequest(
                "POST",
                HttpGatewayHandler.TURN_PATH,
                Map.of(HttpGatewayHandler.SESSION_KEY_HEADER, "reader-session"),
                new GatewayTurnRequest(
                        "web",
                        "conversation-1",
                        "用一句中文解释：为什么 HTTP Gateway 不应该实现自己的 Main Loop？",
                        Map.of("channel", "checkpoint")
                )
        ));
    }

    private static CronTickResult invokeCron(AgentRuntime runtime) {
        List<CronRunRecord> deliveries = new ArrayList<>();
        CronScheduler scheduler = new CronScheduler(runtime, deliveries::add);
        Instant now = Instant.parse("2026-08-09T08:00:00Z");
        CronJob job = new CronJob(
                "daily-review",
                "每日检查",
                "检查项目状态",
                CronSchedule.everyMinutes(60),
                now,
                DeliveryTarget.local("console"),
                false
        );
        CronTickResult result = scheduler.tick(List.of(job), now);
        require(deliveries.size() == 1, "Cron 结果没有交给 Delivery Sink");
        return result;
    }

    private static DelegationResult invokeSubAgent(AgentRuntime runtime) {
        SubAgentRunner runner = new SubAgentRunner(
                runtime,
                new DelegationPolicy(2, IterationBudget.maxTurns(3))
        );
        return runner.run(new DelegationRequest(List.of(new SubAgentTask(
                "inspect-runtime",
                "检查 Runtime 边界",
                "只返回证据摘要",
                List.of("workspace"),
                null
        ))));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
