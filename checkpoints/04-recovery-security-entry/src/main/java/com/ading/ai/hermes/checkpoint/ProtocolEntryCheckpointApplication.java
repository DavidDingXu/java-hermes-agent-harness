package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentRuntime;

public final class ProtocolEntryCheckpointApplication {

    private ProtocolEntryCheckpointApplication() {
    }

    public static void main(String[] args) throws Exception {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        AgentRuntime runtime = request -> readerModel.runText(request.userMessage());
        var gateway = GovernedEntryCheckpointApplication.invokeGateway(runtime);
        var feishu = GovernedEntryCheckpointApplication.invokeFeishu(runtime);
        var acp = GovernedEntryCheckpointApplication.invokeAcp(readerModel);

        require(gateway.ok(), "HTTP Gateway 没有复用 Runtime");
        require("PROCESSED".equals(feishu.status().name()), "飞书事件没有复用 Runtime");
        require(acp.sessionCount() == 2, "ACP Session 新建与分叉没有持久化");
        require(acp.updateCount() >= 2, "ACP 工具更新没有返回 Client");

        System.out.println("[阶段 04-B] Gateway、飞书事件与 ACP 入口运行成功");
        System.out.println("Gateway: HTTP " + gateway.status());
        System.out.println("飞书事件: " + feishu.status());
        System.out.println("ACP: " + acp.sessionCount() + " 条 Session，"
                + acp.updateCount() + " 条工具更新");
        System.out.println("真实模型: " + readerModel.model());
        System.out.println("在线 Gateway 回答: "
                + ReaderModelRuntime.preview(gateway.body().finalAnswer()));
        System.out.println("在线 ACP 回答: " + ReaderModelRuntime.preview(acp.answer()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
