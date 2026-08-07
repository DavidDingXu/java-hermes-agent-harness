# Java Hermes Agent Harness

这是《Java 手写 Hermes Agent Harness：从 Main Loop 到可治理的自进化 Runtime》的配套 Java 项目。

The agent that grows with you. 跟你一起成长的智能体。

目标不是复制 Hermes Agent 的 Python 代码，也不是包装现成 Agent API，而是用 Java 手写代码验证 Hermes 的核心运行时原理。Java 是实现语言；这里沉淀的是 Agent Runtime 的通用边界：

- Agent Main Loop
- ModelProvider、原生 Reasoning 与调用 Metrics
- Tool Registry、稳健 Edit 与完成验证
- Prompt、Context Engine 与上下文压缩
- Context References：文件、目录、Git Diff 与 URL
- Session 持久化
- Memory、Session Search、Skills 与 Learning Graph
- Context Compaction
- Error Recovery
- CLI、本地服务装配、Scheduler、Gateway 与 Subagent
- Toolsets、MCP Adapter 与程序化工具调用
- Workspace Checkpoint、Diff 与 Rollback
- Terminal Backend、Run 生命周期与 Busy Input
- Hooks、Plugins 与 AgentHarness 总装配
- Observability、Trajectory 与 Benchmark
- Coding Agent 最小实战切片
- AgentOps Mini Console

## 当前边界

当前实现覆盖 Runtime 主循环、原生 Reasoning 解析、Provider Runtime 配置解析、工具注册与校验、批量 Tool Call、唯一匹配文本编辑、完成验证门禁、Context Engine、Context References、JSONL 与 SQLite Session、中文 Session Search、Memory、Skills、Learning Graph、HTTP Gateway、可执行 CLI、飞书事件处理核心、Cron、Subagent、Trajectory、模型调用 Metrics、Benchmark、Toolsets/MCP、程序化工具调用、Workspace Checkpoint、Terminal Backend、Run 生命周期、Hooks/Plugins、AgentHarness 总装配、Coding Agent 和静态 AgentOps Console。

CLI、HTTP Gateway、Cron 与 Local Service 都属于入口或装配层，统一调用 `AgentRuntime`。飞书 Channel 只通过 Local Service 接入：`LocalServiceRegistry` 提供类型化服务注册，`FeishuLocalService` 将事件处理器注册为 `feishu.events`；外部 SDK 只负责传输、验签和反序列化，不经过 HTTP Gateway。

真实 HTTP Server、认证、SSE Runs API、Run/Event 持久化、飞书 SDK 与签名校验、持久化事件幂等、自然语言 schedule 解析、Cron 持久化、跨进程锁、工具级取消、后台 subagent、嵌套 orchestrator、面向模型的通用搜索与 Shell Tool、远端 Terminal Backend、Memory 文件持久化、技能候选文件持久化、diff 审批、Trajectory 查询 API、后台复盘线程、真实模型复盘、完整 diff parser、Coding Agent 对 Terminal Backend 的真实接线、git diff 展示和控制台后端接口仍未实现。SQLite Session 已提供本地持久化与 FTS5 搜索实现，但尚未接入默认 CLI 工厂。

## 已实现模块

- `com.dingxu.ai.hermes.RuntimeIdentity`：项目身份。
- `com.dingxu.ai.hermes.runtime.RuntimeBoundaryMap`：Hermes 风格 Runtime 的七条核心边界。
- `com.dingxu.ai.hermes.core.AgentLoop`：受预算约束的最小 Agent Main Loop。
- `com.dingxu.ai.hermes.core.ErrorRecoveringAgentLoop`：把模型异常写成恢复事件，把工具异常写成失败 Observation，并在恢复预算耗尽时停止。
- `com.dingxu.ai.hermes.core.InterruptibleAgentLoop`：读取停止信号，在安全边界写入中断事件并返回 `INTERRUPTED`。
- `com.dingxu.ai.hermes.core.TurnState`：一轮任务的事件、模型轮次、pending tool calls 和结束原因。
- `com.dingxu.ai.hermes.prompt.PromptBuilder`：把系统规则、运行事件、工具规格和模型参数组装成 `ChatRequest`。
- `com.dingxu.ai.hermes.context.ContextFileCollector`：从工作区内按显式路径收集上下文文件，并记录被拒绝的路径和原因。
- `com.dingxu.ai.hermes.context.ContextEngine`：为当前一轮选择上下文的稳定接口。
- `com.dingxu.ai.hermes.context.CompactingContextEngine`：委托现有 Compactor 生成本轮上下文，并通过 Observer 暴露压缩结果。
- `com.dingxu.ai.hermes.context.ContextCompactor`：在上下文超过字符预算时保留头部任务和尾部现场，把中间历史压成带边界的摘要事件。
- `com.dingxu.ai.hermes.session.FileSessionStore`：把 `AgentEvent` 按 sessionId 追加写入 JSONL，并按顺序读回。
- `com.dingxu.ai.hermes.session.SqliteSessionStore`：使用 schema version、WAL、外键与 FTS5 trigram 持久化 Session，并支持中文和工具证据检索。
- `com.dingxu.ai.hermes.session.SessionRestorer`：从持久化事件恢复 `AgentState`，判断 session 可继续、待工具观察或已完成。
- `com.dingxu.ai.hermes.session.RunCheckpointPlanner`：把 `SessionRecord` 转成恢复 checkpoint，保留 lastEventIndex、恢复决策和全部 pending tool requests。
- `com.dingxu.ai.hermes.session.SessionSearchIndex`：从多个 `SessionRecord` 中按关键词查回历史事件，返回 sessionId、事件位置、事件类型和命中片段。
- `com.dingxu.ai.hermes.memory.MemoryPolicy`：判断候选信息能否进入长期记忆，并给出目标、归一化内容和拒绝原因。
- `com.dingxu.ai.hermes.memory.MemoryStore`：把接受的记忆写入内存 store，拒绝重复内容和超过目标容量的内容。
- `com.dingxu.ai.hermes.skill.SkillLoader`：从 skill 目录读取 `SKILL.md`，解析轻量元信息和过程正文。
- `com.dingxu.ai.hermes.skill.SkillResolver`：按当前任务匹配启用的 skill，避免无关技能进入上下文。
- `com.dingxu.ai.hermes.skill.SkillProvenance`：记录 skill 来源类型、来源标识和内容指纹。
- `com.dingxu.ai.hermes.skill.TrustedSkillPolicy`：把 skill 来源和启用状态转换成自动加载、暂存或阻断决策。
- `com.dingxu.ai.hermes.skill.SkillCandidateGenerator`：从任务复盘中生成待审 skill 候选。
- `com.dingxu.ai.hermes.skill.SkillApprovalFlow`：候选先进入 pending，批准后才生成可加载 `SkillManifest`。
- `com.dingxu.ai.hermes.skill.TrajectorySelfImprovementReviewer`：从 `TrajectoryRecord` 中提取 Memory 候选和 Skill Candidate，只有恢复后的失败才进入技能候选生成。
- `com.dingxu.ai.hermes.skill.SelfImprovementLoop`：把 Memory 候选交给 `MemoryStore`，把 Skill Candidate 交给 `SkillApprovalFlow` 的 pending 队列。
- `com.dingxu.ai.hermes.skill.ReviewToolPolicy`：表达复盘阶段只允许 memory / skills 工具的白名单边界。
- `com.dingxu.ai.hermes.learning.LearningGraph`：保存 Memory、Skill、显式 Skill 关系与可解释关联边，并返回不可变快照。
- `com.dingxu.ai.hermes.core.AgentRuntime`：入口层、调度器和后续控制台共同依赖的运行时调用契约。
- `com.dingxu.ai.hermes.cli.JavaHermesCli`：解析 prompt 与最大轮次，调用 `AgentRuntime`，用稳定退出码表达成功、运行失败与配置错误。
- `com.dingxu.ai.hermes.cli.JavaHermesApplication`：从环境变量创建 Provider，注册工作区工具并组装可执行 CLI。
- `com.dingxu.ai.hermes.gateway.HttpGatewayHandler`：校验最小 HTTP envelope，把入口请求转换成 `AgentRunRequest`，再把 Runtime 结果封装成 Gateway 响应。
- `com.dingxu.ai.hermes.gateway.GatewayTurnRequest`：把 CLI、HTTP、消息平台等入口归一成 source、conversationId、userMessage 和 metadata。
- `com.dingxu.ai.hermes.gateway.feishu.FeishuEventHandler`：处理 Challenge、文本校验、事件去重、会话映射、Runtime 调用与回复投递；失败事件允许重试。
- `com.dingxu.ai.hermes.gateway.local.LocalServiceRegistry`：按稳定名称注册类型化本地服务，拒绝重复服务名和错误请求类型。
- `com.dingxu.ai.hermes.gateway.local.FeishuLocalService`：把 `FeishuEventHandler` 注册为 `feishu.events`，服务容器不复制 Runtime 逻辑。
- `com.dingxu.ai.hermes.scheduler.CronScheduler`：在 tick 时选择到期任务、claim fire key、调用同一个 `AgentRuntime`，并把 `CronRunRecord` 交给投递接口。
- `com.dingxu.ai.hermes.scheduler.CronJob`：描述定时任务的 id、name、prompt、schedule、nextRunAt、deliveryTarget 和暂停状态。
- `com.dingxu.ai.hermes.scheduler.CronRunRecord`：记录一次 Cron 执行的 runId、jobId、fireKey、firedAt、nextRunAt、finalAnswer 和 finishReason。
- `com.dingxu.ai.hermes.delegate.SubAgentRunner`：按结构化任务运行隔离子任务，只把 summary、状态、结束原因和预算使用合并回父级结果。
- `com.dingxu.ai.hermes.delegate.SubAgentTask`：描述子任务 id、goal、context、toolsets 和独立预算。
- `com.dingxu.ai.hermes.observability.TrajectoryRecorder`：把运行事件和子 Agent 结果转换成带 sessionId、turnId、taskId 和 parentTurnId 的轨迹事件。
- `com.dingxu.ai.hermes.observability.TraceRedactor`：在轨迹落盘前脱敏 apiKey、token、password、Bearer header 和 `sk-` token。
- `com.dingxu.ai.hermes.observability.FileTrajectoryStore`：把 `TrajectoryRecord` 追加写入 JSONL 文件。
- `com.dingxu.ai.hermes.model.ModelProvider`：模型调用边界。
- `com.dingxu.ai.hermes.provider.ProviderRuntimeResolver`：按请求覆盖、环境变量、Provider Profile 与默认值解析运行配置，隔离 Provider 凭证，并保留 Chat Completions、Codex Responses 和 Anthropic Messages 三种 API 模式。
- `com.dingxu.ai.hermes.model.ModelProviderDriver`：把 `ModelProvider` 接回 Main Loop 的适配器。
- `com.dingxu.ai.hermes.model.ScriptedModelProvider`：不访问网络的确定性测试 provider。
- `com.dingxu.ai.hermes.model.OpenAiCompatibleModelProvider`：调用 OpenAI-compatible `/v1/chat/completions` 接口，解析文本、工具调用、原生 reasoning 和 usage。
- `com.dingxu.ai.hermes.metrics.MeteredModelProvider`：透明记录模型 Provider、Usage、耗时与成功/失败，不改变原调用语义。
- `com.dingxu.ai.hermes.metrics.InMemoryModelMetrics`：保存模型调用指标快照，供测试和最小观测入口使用。
- `com.dingxu.ai.hermes.model.ToolCallParser`：把原始 tool call 解析成请求、修复记录和错误报告。
- `com.dingxu.ai.hermes.tool.ToolRegistry`：按工具名把 `ToolRequest` 分发到 Java 执行器。
- `com.dingxu.ai.hermes.tool.ToolSchema`：在工具执行前校验必填参数和参数类型。
- `com.dingxu.ai.hermes.tool.ToolBatchRunner`：并发执行一组工具请求，按请求顺序回传 Observation，并把工具异常转成失败 Observation。
- `com.dingxu.ai.hermes.security.GuardedToolDriver`：在真实工具执行前运行安全策略，把需要人工确认或不允许的请求转成失败 Observation。
- `com.dingxu.ai.hermes.tools.basic.WorkspaceFileTools`：在工作区边界内读取 UTF-8 文件、列出目录，并把越界、类型错误和超大文件转成失败 Observation。
- `com.dingxu.ai.hermes.tools.basic.UniqueTextEdit`：只允许唯一匹配文本替换，零匹配和多匹配都拒绝修改。
- `com.dingxu.ai.hermes.tools.basic.WorkspaceEditTool`：把唯一匹配编辑能力注册为受工作区约束的 Runtime 工具。
- `com.dingxu.ai.hermes.context.reference.ContextReferenceResolver`：解析并展开 `@file`、`@folder`、`@diff`、`@staged`、`@git` 与 `@url`，执行路径和字符预算检查。
- `com.dingxu.ai.hermes.toolset.ToolsetCatalog`：统一管理工具归属、入口选择和名称唯一性。
- `com.dingxu.ai.hermes.toolset.McpToolAdapter`：把 MCP 工具按来源前缀、include/exclude 与冲突规则注册到 Toolset Catalog。
- `com.dingxu.ai.hermes.programmatic.ProgrammaticToolRuntime`：在允许工具、调用次数、超时和输出上限内执行多步工具程序，只返回聚合结果。
- `com.dingxu.ai.hermes.checkpoint.FileWorkspaceCheckpointStore`：为显式文件建立持久化基线，报告创建、修改、删除并支持保守回滚。
- `com.dingxu.ai.hermes.terminal.LocalProcessTerminalBackend`：以 argv 执行本地进程，限制工作目录与环境变量，处理超时、进程树和输出截断。
- `com.dingxu.ai.hermes.run.InMemoryRunCoordinator`：管理 Active Run、增量事件、审批、Queue、Steer 与 Interrupt 状态。
- `com.dingxu.ai.hermes.hook.RuntimeHookChain`：按优先级执行稳定 Hook，区分失败开放与失败关闭。
- `com.dingxu.ai.hermes.plugin.PluginHost`：让插件通过窄 Context 注册工具和 Hook，不直接改写 Main Loop。
- `com.dingxu.ai.hermes.harness.AgentHarness`：按 Context Reference、Hook、Workspace Checkpoint、AgentRuntime 与 Run 收口顺序完成总体编排。
- `com.dingxu.ai.hermes.runtime.HermesRuntimeFactory`：创建唯一 Harness 主链，并通过 `FeishuLocalService` 把飞书 Handler 注册进 `LocalServiceRegistry`；CLI 和本地服务消费同一份装配结果。
- `com.dingxu.ai.hermes.verification.CompletionGate`：只在最终回答后调用验证器，以结构化证据决定接受或拒绝，不擅自重跑 Agent。
- `com.dingxu.ai.hermes.eval.BenchmarkRunner`：运行正式 `AgentRuntime`，由案例评估器依据结构化证据打分并聚合报告。
- `com.dingxu.ai.hermes.examples.coding.CodingAgentWorkflow`：把上下文收集、结构化模型计划、路径检查、原文匹配、验证命令白名单和 Trajectory 记录串成一次最小改代码任务。
- `com.dingxu.ai.hermes.examples.coding.CodingAgentPolicy`：约束每个上下文文件字符预算和允许执行的验证命令前缀。
- `com.dingxu.ai.hermes.examples.coding.VerificationRunner`：把测试命令执行抽象成可替换接口，当前测试使用记录型 verifier 固定 Runtime 行为。
- `web-console/index.html`：静态 AgentOps Mini Console，用于观察 Gateway、Cron、Approval、Trajectory 和 Self Improvement 的运行时状态。
- `web-console/verify-console.mjs`：检查控制台页面是否包含必要 Runtime 面板、操作钩子和基础结构。
- `docs/runtime-boundary.md`：Runtime 边界说明。

## 运行入口

构建可执行 JAR：

```bash
mvn package
```

配置 OpenAI-compatible 服务后运行：

```bash
export OPENAI_BASE_URL=https://api.openai.com
export OPENAI_API_KEY=你的 API Key
export OPENAI_MODEL=gpt-4.1-mini
java -jar target/java-hermes-agent-harness-0.1.0-SNAPSHOT.jar \
  --prompt "读取 README，并概括当前已实现模块" \
  --max-turns 8
```

`JavaHermesApplication` 调用 `HermesRuntimeFactory` 完成进程内装配，CLI 使用 `assembly.runtime()`；工厂同时通过 `LocalServiceRegistry` 注册飞书 Handler。网络或 SDK adapter 只调用注册服务，不在回调中复制 Runtime。

运行全部测试：

```bash
mvn test
```

默认测试不访问外网。`OpenAiCompatibleModelProviderTest` 使用 fake transport 固定协议行为，检查请求路径、认证头、请求体和响应解析。

需要打真实 OpenAI-compatible 接口时，配置环境变量后运行集成测试：

```bash
export OPENAI_BASE_URL=https://api.openai.com
export OPENAI_API_KEY=你的 API Key
export OPENAI_MODEL=gpt-4.1-mini
mvn test -Dtest=OpenAiCompatibleModelProviderIntegrationTest
```

`OPENAI_BASE_URL` 可以是官方 OpenAI 地址，也可以是任何兼容 `/v1/chat/completions` 的服务地址。没有这些环境变量时，集成测试会自动跳过。

Coding Agent 也提供可选真实接口入口：

```bash
export OPENAI_BASE_URL=https://api.openai.com
export OPENAI_API_KEY=你的 API Key
export OPENAI_MODEL=gpt-4.1-mini
mvn test -Dtest=CodingAgentWorkflowIntegrationTest
```

没有这些环境变量时，测试会自动跳过。离线工作流测试可以直接运行：

```bash
mvn test -Dtest=CodingAgentWorkflowTest
```

最小控制台不需要启动服务，直接打开文件即可：

```text
web-console/index.html
```

控制台结构检查：

```bash
node web-console/verify-console.mjs
```

实现模块时，每个功能都要留下可运行测试：

- 核心类放在清晰的包边界里。
- 行为先用测试固定。
- README 只写读者能运行和核对的信息。

## 参考

- Hermes Agent 官方仓库：<https://github.com/NousResearch/hermes-agent>
- Hermes Agent 官方文档：<https://hermes-agent.nousresearch.com/docs/>
