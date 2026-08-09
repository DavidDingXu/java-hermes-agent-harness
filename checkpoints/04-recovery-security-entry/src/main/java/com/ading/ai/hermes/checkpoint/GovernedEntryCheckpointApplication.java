package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.acp.HermesAcpAgent;
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
import com.ading.ai.hermes.gateway.GatewayAccessPolicy;
import com.ading.ai.hermes.gateway.GatewayIdentity;
import com.ading.ai.hermes.gateway.GatewaySessionRouter;
import com.ading.ai.hermes.gateway.HttpGatewayHandler;
import com.ading.ai.hermes.gateway.HttpGatewayRequest;
import com.ading.ai.hermes.gateway.HttpGatewayResponse;
import com.ading.ai.hermes.gateway.feishu.FeishuEvent;
import com.ading.ai.hermes.gateway.feishu.FeishuEventHandler;
import com.ading.ai.hermes.gateway.feishu.FeishuHandleResult;
import com.ading.ai.hermes.gateway.feishu.FeishuReply;
import com.ading.ai.hermes.model.ToolSpec;
import com.ading.ai.hermes.scheduler.CronJob;
import com.ading.ai.hermes.scheduler.CronRunRecord;
import com.ading.ai.hermes.scheduler.CronSchedule;
import com.ading.ai.hermes.scheduler.CronScheduler;
import com.ading.ai.hermes.scheduler.CronTickResult;
import com.ading.ai.hermes.scheduler.DeliveryTarget;
import com.ading.ai.hermes.security.GuardedToolDriver;
import com.ading.ai.hermes.security.ToolPolicy;
import com.ading.ai.hermes.session.SessionId;
import com.ading.ai.hermes.session.SqliteSessionStore;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GovernedEntryCheckpointApplication {

    private GovernedEntryCheckpointApplication() {
    }

    public static void main(String[] args) throws Exception {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        AgentRunResult recovered = runWithRecovery();
        ToolObservation blocked = verifyGuardrail();
        AgentRuntime runtime = request -> readerModel.runText(request.userMessage());
        HttpGatewayResponse gateway = invokeGateway(runtime);
        FeishuHandleResult feishu = invokeFeishu(runtime);
        AcpCheckpointResult acp = invokeAcp(readerModel);
        CronTickResult cron = invokeCron(runtime);
        DelegationResult delegation = invokeSubAgent(runtime);

        require(recovered.finishReason() == FinishReason.FINAL_ANSWER, "模型错误没有恢复");
        require(blocked.content().contains("blocked"), "Guardrail 没有拦截越界路径");
        require(gateway.ok(), "HTTP Gateway 没有复用 Runtime");
        require("PROCESSED".equals(feishu.status().name()), "飞书入口没有复用 Runtime");
        require(acp.sessionCount() == 2, "ACP Session 新建与分叉没有持久化");
        require(acp.updateCount() >= 2, "ACP 没有发送工具开始和完成更新");
        require(cron.runs().size() == 1, "Cron 没有产生一次运行");
        require(delegation.results().size() == 1, "Subagent 没有返回隔离结果");

        System.out.println("[阶段 04] 可治理入口运行成功");
        System.out.println("错误恢复: " + recovered.finalAnswer());
        System.out.println("Guardrail: " + blocked.content());
        System.out.println("Gateway: HTTP " + gateway.status());
        System.out.println("飞书 Channel: " + feishu.status());
        System.out.println("ACP: " + acp.sessionCount() + " 条 Session，"
                + acp.updateCount() + " 条工具更新");
        System.out.println("Cron: " + cron.runs().getFirst().runId());
        System.out.println("Subagent: " + delegation.results().getFirst().status());
        System.out.println("真实模型: " + readerModel.model());
        System.out.println("在线 Gateway 回答: "
                + ReaderModelRuntime.preview(gateway.body().finalAnswer()));
        System.out.println("在线 ACP 回答: " + ReaderModelRuntime.preview(acp.answer()));
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

    private static FeishuHandleResult invokeFeishu(AgentRuntime runtime) {
        List<FeishuReply> replies = new ArrayList<>();
        GatewayIdentity identity = new GatewayIdentity(
                "feishu",
                "group",
                "reader-chat",
                "reader-user"
        );
        String sessionKey = new GatewaySessionRouter().sessionKey(identity);
        require(
                "agent:main:feishu:group:reader-chat".equals(sessionKey),
                "Gateway Session Key 不稳定"
        );
        GatewayAccessPolicy access = GatewayAccessPolicy.allowList(Set.of("reader-user"));
        require(access.evaluate(identity).allowed(), "白名单用户没有通过入口鉴权");
        require(!access.evaluate(new GatewayIdentity(
                "feishu", "group", "reader-chat", "unknown-user"
        )).allowed(), "未授权用户绕过了入口鉴权");

        FeishuEventHandler handler = new FeishuEventHandler(
                runtime,
                replies::add,
                com.ading.ai.hermes.control.NewWorkPolicy.allowAll(),
                access
        );
        FeishuHandleResult result = handler.handle(FeishuEvent.text(
                "event-reader-1",
                "group",
                "reader-chat",
                "reader-user",
                "用一句中文解释：为什么消息入口必须先完成鉴权和会话路由？"
        ));
        require(replies.size() == 1, "飞书回复没有进入 Reply Sink");
        return result;
    }

    private static AcpCheckpointResult invokeAcp(ReaderModelRuntime readerModel) throws Exception {
        Path workspace = Files.createTempDirectory("hermes-acp-checkpoint-");
        try {
            SqliteSessionStore sessions = new SqliteSessionStore(workspace.resolve("sessions.db"));
            AtomicReference<String> cancelledSession = new AtomicReference<>();
            ToolSpec inspectAcp = new ToolSpec(
                    "inspect_acp",
                    "第一轮必须且只能调用一次，读取当前 ACP Session 的协议证据。",
                    Map.of()
            );
            AgentRuntime runtime = request -> {
                AgentRunResult result = readerModel.run(
                        """
                                你正在通过 ACP 验证 Hermes Runtime。
                                第一轮必须且只能调用一次 inspect_acp。
                                看到 Observation 后立即用一句中文说明 ACP Adapter 为什么不能实现第二套 Main Loop。
                                """,
                        request.userMessage(),
                        toolRequest -> ToolObservation.success(
                                toolRequest.callId(),
                                "ACP Session 通过官方 Java SDK 进入共享 Runtime。"
                        ),
                        List.of(inspectAcp),
                        request.budget().maxTurns()
                );
                result.state().events().forEach(event -> sessions.append(
                        new SessionId(request.conversationId()),
                        event
                ));
                return result;
            };
            HermesAcpAgent agent = new HermesAcpAgent(
                    runtime,
                    cancelledSession::set,
                    sessions,
                    workspace,
                    readerModel.model()
            );
            AcpSchema.InitializeResponse initialized = agent.initialize(
                    new AcpSchema.InitializeRequest(
                            AcpSchema.LATEST_PROTOCOL_VERSION,
                            new AcpSchema.ClientCapabilities()
                    )
            );
            require(initialized.protocolVersion() == AcpSchema.LATEST_PROTOCOL_VERSION,
                    "ACP 协议版本协商失败");

            AcpSchema.NewSessionResponse created = agent.newSession(
                    new AcpSchema.NewSessionRequest(workspace.toString(), List.of())
            );
            List<AcpSchema.SessionUpdate> updates = new ArrayList<>();
            List<String> messages = new ArrayList<>();
            SyncPromptContext context = promptContext(created.sessionId(), updates, messages);
            AcpSchema.PromptResponse prompted = agent.prompt(
                    new AcpSchema.PromptRequest(
                            created.sessionId(),
                            List.of(new AcpSchema.TextContent(
                                    "检查当前 ACP 入口并给出协议边界结论"
                            ))
                    ),
                    context
            );
            require(prompted.stopReason() == AcpSchema.StopReason.END_TURN,
                    "ACP 在线 Prompt 没有正常结束");
            require(updates.stream().anyMatch(AcpSchema.ToolCall.class::isInstance),
                    "ACP 没有发送工具开始更新");
            require(updates.stream().anyMatch(
                    AcpSchema.ToolCallUpdateNotification.class::isInstance
            ), "ACP 没有发送工具完成更新");
            require(messages.size() == 1 && !messages.getFirst().isBlank(),
                    "ACP 没有发送最终消息");

            agent.setSessionConfigOption(AcpSchema.SetSessionConfigOptionRequest.select(
                    created.sessionId(),
                    "model",
                    readerModel.model()
            ));
            AcpSchema.ForkSessionResponse forked = agent.forkSession(
                    new AcpSchema.ForkSessionRequest(
                            created.sessionId(),
                            workspace.toString(),
                            List.of()
                    )
            );
            List<AcpSchema.SessionUpdate> replayed = new ArrayList<>();
            agent.loadSession(new AcpSchema.LoadSessionRequest(
                    forked.sessionId(),
                    workspace.toString(),
                    List.of()
            ), replayed::add);
            require(replayed.stream().anyMatch(AcpSchema.UserMessageChunk.class::isInstance),
                    "ACP Load 没有重放用户历史");
            require(replayed.stream().anyMatch(AcpSchema.AgentMessageChunk.class::isInstance),
                    "ACP Load 没有重放模型历史");

            int sessionCount = agent.listSessions(
                    new AcpSchema.ListSessionsRequest(null)
            ).sessions().size();
            agent.cancel(new AcpSchema.CancelNotification(forked.sessionId()));
            require(forked.sessionId().equals(cancelledSession.get()),
                    "ACP Cancel 没有到达共享取消端口");
            return new AcpCheckpointResult(
                    messages.getFirst(),
                    sessionCount,
                    updates.size()
            );
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static SyncPromptContext promptContext(
            String sessionId,
            List<AcpSchema.SessionUpdate> updates,
            List<String> messages
    ) {
        return (SyncPromptContext) Proxy.newProxyInstance(
                SyncPromptContext.class.getClassLoader(),
                new Class<?>[]{SyncPromptContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getSessionId" -> sessionId;
                    case "sendUpdate" -> {
                        updates.add((AcpSchema.SessionUpdate) arguments[1]);
                        yield null;
                    }
                    case "sendMessage" -> {
                        messages.add((String) arguments[0]);
                        yield null;
                    }
                    case "toString" -> "CheckpointSyncPromptContext";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
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

    private static void deleteRecursively(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record AcpCheckpointResult(String answer, int sessionCount, int updateCount) {
    }
}
