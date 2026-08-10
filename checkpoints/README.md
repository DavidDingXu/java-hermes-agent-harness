# 分阶段源码快照

每个目录都是一份可单独打开、可直接运行 Main 方法的累计源码快照。用 IDEA 打开对应目录，等待依赖索引完成后，直接运行表中的 Main 即可；不需要先编译根项目。读者可以先看当前阶段的全部代码，再对照下一个快照观察 Runtime 如何演进。

| 学习进度 | 源码快照 | 直接运行的 Main | 可观察结果 |
|---|---|---|---|
| 完成 06 | `01-main-loop` | `com.ading.ai.hermes.checkpoint.MainLoopCheckpointApplication` | 真实模型决策、Reasoning、Tool Call 修复、工具 Observation 与最终回答 |
| 完成 10 | `02-tools` | `com.ading.ai.hermes.checkpoint.ToolRuntimeCheckpointApplication` | 真实模型完成 `read_file -> edit_file -> FINAL_ANSWER`，并检查磁盘结果 |
| 完成 17 | `03-context-session-memory` | `com.ading.ai.hermes.checkpoint.StateCheckpointApplication` | 真实模型同时使用 Context、SQLite Session、Memory 与 Skill |
| 完成 20 | `04-recovery-security-entry` | `com.ading.ai.hermes.checkpoint.RecoverySecurityCheckpointApplication` | 恢复预算、失败分类与 Guardrail |
| 完成 24 | `04-recovery-security-entry` | `com.ading.ai.hermes.checkpoint.ProtocolEntryCheckpointApplication` | Gateway、飞书事件核心与 ACP 复用 Runtime |
| 完成 25 | `04-recovery-security-entry` | `com.ading.ai.hermes.checkpoint.CronCheckpointApplication` | 计划触发、模型运行与独立投递状态 |
| 完成 26 | `04-recovery-security-entry` | `com.ading.ai.hermes.checkpoint.SubAgentCheckpointApplication` | 独立子会话、工具集与预算 |
| 完成 26 | `04-recovery-security-entry` | `com.ading.ai.hermes.checkpoint.GovernedEntryCheckpointApplication` | 阶段 04 全链路累计验收 |
| 完成 31 | `05-observability-learning` | `com.ading.ai.hermes.checkpoint.ObservabilityCheckpointApplication` | 真实模型调用产生 Metrics、Trajectory、Benchmark 与待审改进候选 |
| 完成 37 | `06-advanced-harness` | `com.ading.ai.hermes.checkpoint.AdvancedHarnessCheckpointApplication` | 真实模型调用插件工具，并经过 Checkpoint、Run、Hooks 与 AgentHarness |

第一次运行前，在项目根目录创建 `config/hermes.local.properties`：

```properties
openai.base-url=https://your-openai-compatible-endpoint
openai.api-key=你的_API_Key
openai.model=支持_Tool_Call_的模型名
```

所有阶段 Main 都只从这份本地私有文件读取配置，并且都会发起真实模型请求。配置缺失、仍是示例值、模型请求失败或阶段事实不成立时，程序会明确失败。文件已被 Git 忽略，同一份配置可供全部快照重复使用。

每个目录都有自己的 `README.md`，列出建议先读的 5～6 个文件和验收标准。阶段工程还带有聚焦契约测试，用来守住协议、安全和状态边界；读者学习时仍以直接运行 Main、观察真实模型行为为主。

完成 38 后，再转到项目根目录运行 `JavaHermesApplication.main()`、`HermesWebApplication.main()`，或由 Agent Client 启动 `AcpApplication.main()`。根目录的完整工程负责把六个阶段装配成 CLI、持久化运行状态、Web 控制台与标准协议入口；真实模型不是等到这一步才出现。
