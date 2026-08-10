package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.scheduler.CronDeliveryStatus;

public final class CronCheckpointApplication {

    private CronCheckpointApplication() {
    }

    public static void main(String[] args) {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        AgentRuntime runtime = request -> readerModel.runText(request.userMessage());
        var result = GovernedEntryCheckpointApplication.invokeCron(runtime);

        require(result.runs().size() == 1, "Cron 没有产生运行记录");
        require(result.deliveries().getFirst().status() == CronDeliveryStatus.DELIVERED,
                "Cron 结果没有成功投递");

        var run = result.runs().getFirst();
        require(run.finalAnswer().contains("pom.xml") && run.finalAnswer().contains("Java 21"),
                "Cron 在线摘要没有忠实使用巡检证据");
        System.out.println("[阶段 04-C] Cron 主动运行成功");
        System.out.println("计划触发: " + run.fireKey());
        System.out.println("下次运行: " + run.nextRunAt());
        System.out.println("投递状态: " + result.deliveries().getFirst().status());
        System.out.println("真实模型: " + readerModel.model());
        System.out.println("在线回答: " + ReaderModelRuntime.preview(run.finalAnswer()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
