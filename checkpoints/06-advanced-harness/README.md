# 阶段 06：高级 Agent Harness

对应第 32～37 篇。这个快照把高级能力放到明确的治理边界内，再由 `AgentHarness` 统一编排。

## 先读这 6 个文件

1. `toolset/ToolsetCatalog.java`：按任务选择真实工具权限。
2. `programmatic/ProgrammaticToolRuntime.java`：受预算约束的多步工具程序。
3. `checkpoint/FileWorkspaceCheckpointStore.java`：Diff 与保守回滚。
4. `terminal/LocalProcessTerminalBackend.java`：工作目录、环境与输出隔离。
5. `run/InMemoryRunCoordinator.java`：Run 事件、停止与 Busy Input。
6. `harness/AgentHarness.java`：把以上边界按固定顺序收口。

## 运行验收

直接运行 `AdvancedHarnessCheckpointApplication.main()`。真实模型会调用插件注册的工具，程序同时验证 Context Reference、Workspace Checkpoint、Run、Hook 与 AgentHarness 的结构化事实。

完成后进入第 38 篇，把六个阶段装配成可直接使用的 CLI、Web Console 与 ACP Agent。
