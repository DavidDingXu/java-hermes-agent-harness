package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.context.reference.ContextReferenceResolver;
import com.ading.ai.hermes.context.reference.UrlContextFetcher;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.harness.AgentHarness;
import com.ading.ai.hermes.harness.HarnessRunRequest;
import com.ading.ai.hermes.harness.HarnessRunResult;
import com.ading.ai.hermes.harness.HarnessRunStatus;
import com.ading.ai.hermes.hook.HookFailureMode;
import com.ading.ai.hermes.hook.RuntimeHookDecision;
import com.ading.ai.hermes.hook.RuntimeHookPoint;
import com.ading.ai.hermes.plugin.PluginHost;
import com.ading.ai.hermes.programmatic.ProgrammaticToolRequest;
import com.ading.ai.hermes.programmatic.ProgrammaticToolResult;
import com.ading.ai.hermes.programmatic.ProgrammaticToolRuntime;
import com.ading.ai.hermes.programmatic.ProgrammaticToolStatus;
import com.ading.ai.hermes.run.InMemoryRunCoordinator;
import com.ading.ai.hermes.run.RunStatus;
import com.ading.ai.hermes.tool.ToolDefinition;
import com.ading.ai.hermes.tool.ToolResult;
import com.ading.ai.hermes.tool.ToolSchema;
import com.ading.ai.hermes.toolset.ToolsetSelection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class AdvancedHarnessCheckpointApplication {

    private AdvancedHarnessCheckpointApplication() {
    }

    public static void main(String[] args) throws Exception {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        Path workspace = Files.createTempDirectory("hermes-harness-");
        try {
            Files.writeString(workspace.resolve("README.md"), "before\n", StandardCharsets.UTF_8);
            PluginHost plugins = installWorkspacePlugin(workspace);
            ToolsetSelection toolset = plugins.toolsets().select(Set.of("workspace"));
            ProgrammaticToolResult program = runProgram(toolset);
            AgentRunResult liveResult = readerModel.run(
                    "必须调用 read_marker 获取工作区标记，然后用一句中文说明读取结果。",
                    toolset.registry(),
                    toolset.specs(),
                    4
            );

            FileWorkspaceCheckpointStore checkpoints = new FileWorkspaceCheckpointStore(workspace);
            InMemoryRunCoordinator runs = new InMemoryRunCoordinator();
            AtomicReference<String> runtimeInput = new AtomicReference<>();
            AgentHarness harness = new AgentHarness(
                    (request, stopSignal) -> {
                        runtimeInput.set(request.userMessage());
                        writeWorkspaceFile(workspace.resolve("README.md"), "after\n");
                        return new AgentRunResult(
                                FinishReason.FINAL_ANSWER,
                                "Harness 总装配完成",
                                AgentState.start(request.userMessage())
                        );
                    },
                    new ContextReferenceResolver(
                            workspace,
                            8_192,
                            UrlContextFetcher.disabled(),
                            reference -> ""
                    ),
                    checkpoints,
                    runs,
                    plugins.hooks()
            );

            HarnessRunResult result = harness.run(new HarnessRunRequest(
                    AgentRunRequest.from(
                            "checkpoint",
                            "reader-session",
                            "检查 @file:README.md",
                            IterationBudget.maxTurns(4),
                            Map.of()
                    ),
                    List.of("README.md")
            ));
            String checkpointId = result.checkpoint().orElseThrow().id();

            require(program.status() == ProgrammaticToolStatus.SUCCESS, "程序化工具链运行失败");
            require("before".equals(program.output()), "程序化工具没有读取预期内容");
            require(liveResult.finishReason() == FinishReason.FINAL_ANSWER, "真实模型没有正常回答");
            require(liveResult.state().events().stream().anyMatch(event ->
                            event.kind() == AgentEventKind.TOOL_OBSERVED
                                    && event.toolObservation().success()
                                    && event.toolObservation().content().contains("before")),
                    "真实模型没有完成 read_marker 工具闭环");
            require(result.status() == HarnessRunStatus.COMPLETED, "AgentHarness 没有正常完成");
            require(runtimeInput.get().contains("--- Attached Context ---"), "Context 引用没有装配");
            require(runtimeInput.get().contains("[plugin hook]"), "Plugin Hook 没有生效");
            require(checkpoints.diff(checkpointId).getFirst().kind() == WorkspaceChangeKind.MODIFIED,
                    "Checkpoint 没有识别工作区变更");
            require(runs.snapshot(result.runId()).status() == RunStatus.COMPLETED,
                    "Run Coordinator 没有完成生命周期收口");

            checkpoints.rollback(checkpointId);
            require("before".equals(Files.readString(workspace.resolve("README.md")).trim()),
                    "Rollback 没有恢复工作区");

            System.out.println("[阶段 06] Advanced Agent Harness 运行成功");
            System.out.println("Toolset: " + toolset.toolNamesByToolset());
            System.out.println("Programmatic Tool Calls: " + program.toolCalls());
            System.out.println("Run ID: " + result.runId());
            System.out.println("Checkpoint: " + checkpointId + "（已回滚验证）");
            System.out.println("最终回答: " + result.message());
            System.out.println("真实模型: " + readerModel.model());
            System.out.println("在线 Toolset 回答: "
                    + ReaderModelRuntime.preview(liveResult.finalAnswer()));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static PluginHost installWorkspacePlugin(Path workspace) {
        ToolDefinition readMarker = new ToolDefinition(
                "read_marker",
                "Read the checkpoint marker",
                ToolSchema.object(),
                request -> {
                    try {
                        return ToolResult.success(
                                request.callId(),
                                Files.readString(workspace.resolve("README.md")).trim()
                        );
                    } catch (Exception error) {
                        return ToolResult.failure(request.callId(), error.getMessage());
                    }
                }
        );
        return PluginHost.empty().install(context -> {
            context.registerTool("workspace", readMarker);
            context.registerHook(
                    "reader-context-marker",
                    RuntimeHookPoint.BEFORE_RUN,
                    10,
                    HookFailureMode.FAIL_CLOSED,
                    event -> {
                        Map<String, Object> payload = new LinkedHashMap<>(event.payload());
                        payload.put("message", payload.get("message") + "\n[plugin hook]");
                        return RuntimeHookDecision.continueWith(payload);
                    }
            );
        });
    }

    private static ProgrammaticToolResult runProgram(ToolsetSelection toolset) {
        ProgrammaticToolRuntime runtime = new ProgrammaticToolRuntime(toolset.registry());
        return runtime.execute(new ProgrammaticToolRequest(
                "read-workspace-marker",
                context -> context.call("read_marker", Map.of()).content(),
                Set.of("read_marker"),
                1,
                Duration.ofSeconds(2)
        ));
    }

    private static void writeWorkspaceFile(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("failed to update checkpoint workspace", error);
        }
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
