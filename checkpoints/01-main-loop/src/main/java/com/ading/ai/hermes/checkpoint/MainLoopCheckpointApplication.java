package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.model.RawToolCall;
import com.ading.ai.hermes.model.ToolCallParseReport;
import com.ading.ai.hermes.model.ToolCallParser;
import com.ading.ai.hermes.model.ToolSpec;
import java.util.List;
import java.util.Map;

public final class MainLoopCheckpointApplication {

    private MainLoopCheckpointApplication() {
    }

    public static void main(String[] args) {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        ToolSpec markerTool = new ToolSpec(
                "read_checkpoint_marker",
                "读取阶段 01 的在线验收标记",
                Map.of()
        );
        AgentRunResult result = readerModel.run(
                "必须先调用 read_checkpoint_marker，再根据工具返回内容说明阶段 01 是否在线运行成功。",
                request -> request.name().equals(markerTool.name())
                        ? ToolObservation.success(
                                request.callId(),
                                "Hermes 阶段 01 已由真实模型进入 Main Loop。"
                        )
                        : ToolObservation.failure(request.callId(), "未知工具: " + request.name()),
                List.of(markerTool),
                4
        );

        long observations = result.state().events().stream()
                .filter(event -> event.kind() == AgentEventKind.TOOL_OBSERVED)
                .count();
        ToolCallParseReport parseReport = new ToolCallParser().parse(List.of(
                new RawToolCall("", markerTool.name(), "{}"),
                new RawToolCall("broken-call", markerTool.name(), "{")
        ));
        require(result.finishReason() == FinishReason.FINAL_ANSWER, "运行没有正常完成");
        require(readerModel.modelCalls() >= 2, "真实模型没有完成工具往返");
        require(observations == 1, "工具结果没有回填上下文");
        require(parseReport.requests().size() == 1, "可修复的 Tool Call 没有进入主链");
        require(parseReport.repairs().size() == 1, "缺失 call id 没有留下修复记录");
        require(parseReport.errors().size() == 1, "损坏的 arguments JSON 没有报错");

        System.out.println("[阶段 01] Main Loop 运行成功");
        System.out.println("真实模型: " + readerModel.model());
        System.out.println("模型调用: " + readerModel.modelCalls() + " 次");
        System.out.println("工具 Observation: " + observations + " 条");
        System.out.println("Tool Call: " + parseReport.repairs().size() + " 条修复，"
                + parseReport.errors().size() + " 条错误");
        if (!readerModel.reasoningEvidence().isEmpty()) {
            System.out.println("Reasoning 证据: "
                    + ReaderModelRuntime.preview(readerModel.reasoningEvidence().getFirst()));
        }
        System.out.println("在线回答: " + ReaderModelRuntime.preview(result.finalAnswer()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
