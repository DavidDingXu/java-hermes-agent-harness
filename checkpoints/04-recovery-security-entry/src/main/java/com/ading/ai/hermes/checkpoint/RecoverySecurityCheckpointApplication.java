package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.prompt.SystemReminderPolicy;
import com.ading.ai.hermes.verification.CompletionEvidence;
import com.ading.ai.hermes.verification.CompletionGate;
import java.util.Map;

public final class RecoverySecurityCheckpointApplication {

    private RecoverySecurityCheckpointApplication() {
    }

    public static void main(String[] args) {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        var recovered = GovernedEntryCheckpointApplication.runWithRecovery();
        var blocked = GovernedEntryCheckpointApplication.verifyGuardrail();
        var online = readerModel.runText(
                "用一句中文说明：为什么工具系统异常要受恢复预算约束，而路径越界必须直接拦截？"
        );
        var reminderState = AgentState.start("读取项目配置")
                .append(AgentEvent.toolRequested(new ToolRequest(
                        "call-read-config",
                        "read_file",
                        Map.of("path", "config/application.yml")
                )))
                .append(AgentEvent.toolObserved(ToolObservation.executionFailure(
                        "call-read-config",
                        "file is temporarily unavailable"
                )));
        var reminders = SystemReminderPolicy.standard().remindersFor(reminderState);
        var rejectedCompletion = new CompletionGate(result -> CompletionEvidence.reject(
                "本阶段故意模拟验证未通过，确认 Runtime 不会把回答直接当成完成证据"
        )).evaluate(online);

        require(recovered.finishReason() == FinishReason.FINAL_ANSWER, "恢复循环没有正常完成");
        require(blocked.content().contains("blocked"), "Guardrail 没有拦截越界路径");
        require(online.finishReason() == FinishReason.FINAL_ANSWER, "真实模型没有正常回答");
        require(
                reminders.stream().anyMatch(reminder -> "tool-failure".equals(reminder.code())),
                "失败工具没有生成 System Reminder"
        );
        require(
                rejectedCompletion.eligible() && !rejectedCompletion.accepted(),
                "Completion Gate 没有拒绝缺少证据的完成声明"
        );

        System.out.println("[阶段 04-A] 恢复、安全提醒与完成门禁运行成功");
        System.out.println("恢复结果: " + recovered.finalAnswer());
        System.out.println("越界拦截: " + blocked.content());
        System.out.println("运行时提醒: " + reminders.getFirst().text());
        System.out.println("完成门禁: " + rejectedCompletion.detail());
        System.out.println("真实模型: " + readerModel.model());
        System.out.println("在线回答: " + ReaderModelRuntime.preview(online.finalAnswer()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
