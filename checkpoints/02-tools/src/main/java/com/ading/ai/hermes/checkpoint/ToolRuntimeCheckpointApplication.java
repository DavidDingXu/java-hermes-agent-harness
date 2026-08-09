package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.tool.ToolBatchRunner;
import com.ading.ai.hermes.tool.ToolRegistry;
import com.ading.ai.hermes.tools.basic.WorkspaceEditTool;
import com.ading.ai.hermes.tools.basic.WorkspaceFileTools;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ToolRuntimeCheckpointApplication {

    private ToolRuntimeCheckpointApplication() {
    }

    public static void main(String[] args) throws Exception {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        Path workspace = Files.createTempDirectory("hermes-tools-");
        try {
            Files.writeString(workspace.resolve("README.md"), "状态：待验证\n", StandardCharsets.UTF_8);
            Files.writeString(workspace.resolve("NOTES.md"), "工具结果必须返回 Observation。\n", StandardCharsets.UTF_8);

            ToolRegistry registry = new WorkspaceFileTools(workspace, 8_192)
                    .registerInto(ToolRegistry.empty());
            registry = new WorkspaceEditTool(workspace).registerInto(registry);
            ToolBatchRunner tools = new ToolBatchRunner(
                    registry,
                    2,
                    request -> !request.name().equals("edit_file")
            );

            List<ToolObservation> reads = tools.execute(List.of(
                    request("read-1", "read_file", Map.of("path", "README.md")),
                    request("read-2", "read_file", Map.of("path", "NOTES.md"))
            ));
            ToolObservation edit = tools.execute(request("edit-1", "edit_file", Map.of(
                    "path", "README.md",
                    "expected", "待验证",
                    "replacement", "已验证"
            )));
            ToolObservation invalid = tools.execute(request(
                    "read-3",
                    "read_file",
                    Map.of("unknown", "README.md")
            ));

            require(reads.stream().allMatch(ToolObservation::success), "并发读取失败");
            require(edit.success(), "唯一文本编辑失败");
            require(!invalid.success(), "Schema 没有拒绝非法参数");
            require(Files.readString(workspace.resolve("README.md")).contains("已验证"), "文件没有更新");

            System.out.println("[阶段 02] Tool Runtime 运行成功");
            System.out.println("已注册工具: " + registry.specs().stream().map(spec -> spec.name()).toList());
            System.out.println("并发读取: " + reads.size() + " 个结果，顺序与请求一致");
            System.out.println("Schema 拒绝: " + invalid.content());
            System.out.println("编辑结果: " + Files.readString(workspace.resolve("README.md")).trim());

            runRealModelCheckpoint(readerModel, registry, tools);
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static ToolRequest request(String id, String name, Map<String, Object> arguments) {
        return new ToolRequest(id, name, arguments);
    }

    private static void runRealModelCheckpoint(
            ReaderModelRuntime readerModel,
            ToolRegistry registry,
            ToolBatchRunner tools
    ) {
        var readSpecs = registry.specs().stream()
                .filter(spec -> spec.name().equals("read_file") || spec.name().equals("edit_file"))
                .toList();
        AgentRunResult liveResult = readerModel.run(
                """
                        必须先调用 read_file 读取 README.md。
                        确认原文后，调用 edit_file 把唯一匹配的“状态：已验证”改成“状态：在线验证”。
                        工具成功后，用一句中文说明最终状态。
                        """,
                tools,
                readSpecs,
                6
        );
        boolean observedFile = liveResult.state().events().stream()
                .filter(event -> event.kind() == AgentEventKind.TOOL_OBSERVED)
                .map(event -> event.toolObservation())
                .anyMatch(observation -> observation.success()
                        && observation.content().contains("状态：已验证"));
        boolean requestedEdit = liveResult.state().events().stream()
                .filter(event -> event.kind() == AgentEventKind.TOOL_REQUESTED)
                .map(event -> event.toolRequest())
                .anyMatch(request -> request.name().equals("edit_file"));
        require(liveResult.finishReason() == FinishReason.FINAL_ANSWER, "真实模型没有正常回答");
        require(observedFile, "真实模型没有完成 read_file 工具闭环");
        require(requestedEdit, "真实模型没有请求 edit_file");
        require(readWorkspaceState(registry).contains("在线验证"), "真实模型没有完成文件编辑");

        System.out.println("真实模型: " + readerModel.model());
        System.out.println("在线工具闭环: read_file -> edit_file -> FINAL_ANSWER");
        System.out.println("在线文件结果: 状态：在线验证");
        System.out.println("在线回答: " + ReaderModelRuntime.preview(liveResult.finalAnswer()));
    }

    private static String readWorkspaceState(ToolRegistry registry) {
        ToolObservation observation = registry.execute(request(
                "verify-online-edit",
                "read_file",
                Map.of("path", "README.md")
        ));
        require(observation.success(), "无法读取在线编辑结果");
        return observation.content();
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
}
