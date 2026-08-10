package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.delegate.DelegationStatus;

public final class SubAgentCheckpointApplication {

    private SubAgentCheckpointApplication() {
    }

    public static void main(String[] args) {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        AgentRuntime runtime = request -> readerModel.runText(request.userMessage());
        var result = GovernedEntryCheckpointApplication.invokeSubAgent(runtime);
        var child = result.results().getFirst();

        require(child.status() == DelegationStatus.COMPLETED, "SubAgent 没有正常完成");
        require(child.conversationId().contains("subagent"), "SubAgent 没有独立会话");
        require(child.toolsets().equals(java.util.List.of("workspace-read")),
                "SubAgent 工具集没有按任务收窄");
        require(child.summary().contains("AgentRuntime"),
                "SubAgent 在线摘要没有忠实使用给定的边界证据");

        System.out.println("[阶段 04-D] SubAgent 隔离运行成功");
        System.out.println("子会话: " + child.conversationId());
        System.out.println("工具集: " + child.toolsets());
        System.out.println("轮次预算: " + child.budget().maxTurns());
        System.out.println("真实模型: " + readerModel.model());
        System.out.println("在线摘要: " + ReaderModelRuntime.preview(child.summary()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
