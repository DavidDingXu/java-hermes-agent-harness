# Java Hermes Agent Harness

这是《Java 手写 Hermes Agent Harness：从 Main Loop 到可治理的自进化 Runtime》的配套 Java 项目。

The agent that grows with you. 跟你一起成长的智能体。

目标不是复制 Hermes Agent 的 Python 代码，也不是包装现成 Agent API，而是用 Java 手写代码验证 Hermes 的核心运行时原理。Java 是实现语言；这里沉淀的是 Agent Runtime 的通用边界：

- Agent Main Loop
- ModelProvider、原生 Reasoning 与调用 Metrics
- Tool Registry、稳健 Edit 与完成验证
- Prompt 三层计划、稳定前缀证据、Context Engine 与上下文压缩
- Context References：文件、目录、Git Diff 与 URL
- Session 持久化、Lineage、Profile 与恢复配置
- Memory、Session Search、Skills 与 Learning Graph
- Context Compaction
- Error Recovery
- CLI、本地服务装配、Scheduler、Gateway 身份路由与 Subagent
- Agent Client Protocol 标准输入输出入口
- Toolsets、MCP Adapter 与程序化工具调用
- Workspace Checkpoint、Diff 与 Rollback
- Terminal Backend、Run 生命周期与 Busy Input
- Hooks、Plugins 与 AgentHarness 总装配
- Observability、Trajectory 与 Benchmark
- Coding Agent 最小实战切片
- 可配置 Web Console 与真实运行轨迹

## 先按阶段运行

不需要等到所有章节结束才看得到可运行代码。`checkpoints/` 下有 6 份独立的累计源码快照，每份都有自己的 `src/main/java`、构建描述和 Main 入口，不依赖根项目的编译结果。

| 完成进度 | 源码目录 | 直接运行的 Main |
| --- | --- | --- |
| 第 06 篇 | `checkpoints/01-main-loop` | `MainLoopCheckpointApplication.main()` |
| 第 10 篇 | `checkpoints/02-tools` | `ToolRuntimeCheckpointApplication.main()` |
| 第 17 篇 | `checkpoints/03-context-session-memory` | `StateCheckpointApplication.main()` |
| 第 20 篇 | `checkpoints/04-recovery-security-entry` | `RecoverySecurityCheckpointApplication.main()` |
| 第 24 篇 | `checkpoints/04-recovery-security-entry` | `ProtocolEntryCheckpointApplication.main()` |
| 第 25 篇 | `checkpoints/04-recovery-security-entry` | `CronCheckpointApplication.main()` |
| 第 26 篇 | `checkpoints/04-recovery-security-entry` | `SubAgentCheckpointApplication.main()`、`GovernedEntryCheckpointApplication.main()` |
| 第 31 篇 | `checkpoints/05-observability-learning` | `ObservabilityCheckpointApplication.main()` |
| 第 37 篇 | `checkpoints/06-advanced-harness` | `AdvancedHarnessCheckpointApplication.main()` |

所有阶段 Main 都读取项目根目录下已忽略的 `config/hermes.local.properties`，调用真实 OpenAI-compatible 模型，再检查当前阶段的结构化事实。模型调用失败、工具没有执行或磁盘结果不正确时，程序会直接失败。详细说明见 [`checkpoints/README.md`](checkpoints/README.md)。

每个快照目录都有一份短 README，只列当前阶段最值得先读的文件、Main 入口和成功标准。建议先按这份路线阅读，不要从包目录第一行开始逐类翻看。

完成第 38 篇后，再回到根项目运行 `JavaHermesApplication.main()`、`HermesWebApplication.main()`，或由 Agent Client 启动 `AcpApplication.main()`，验证最终装配。

## 当前边界

当前实现覆盖 Runtime 主循环、原生 Reasoning 解析、Provider Runtime 配置解析、工具注册与校验、批量 Tool Call、唯一匹配文本编辑、完成验证门禁、Prompt 三层计划、Context Engine、Context References、JSONL 与 SQLite Session、Session Lineage/配置/Profile、中文 Session Search、Memory、Skills、Learning Graph、HTTP Gateway、Gateway 鉴权与稳定会话路由、ACP 官方 Java SDK 入口、可执行 CLI、飞书事件处理核心、Cron、Subagent、Trajectory、模型调用 Metrics、Benchmark、Toolsets/MCP、程序化工具调用、Workspace Checkpoint、Terminal Backend、Run 生命周期、Hooks/Plugins、AgentHarness 总装配、Coding Agent，以及可配置、可提交真实任务的 Web Console。

CLI、HTTP Gateway、Cron、ACP 与 Local Service 都属于入口或装配层，统一调用 `AgentRuntime`。飞书 Channel 只通过 Local Service 接入：`LocalServiceRegistry` 提供类型化服务注册，`FeishuLocalService` 将事件处理器注册为 `feishu.events`；外部 SDK 只负责传输、验签和反序列化，不经过 HTTP Gateway。ACP 则由官方 Java SDK 处理 JSON-RPC 与 stdio，`HermesAcpAgent` 负责 Session 和事件转换。

默认 CLI 与 Web 主链已接入 SQLite Session、FTS5 检索、JSONL Trajectory、模型指标、持久化 Memory、Skill 候选/批准文件和工具 Guardrails。Web Console 可查看运行指标、检索 Session，并审批或拒绝待审 Skill。

生产化边界仍然清晰：当前 Web 只监听本机回环地址，任务同步执行，没有多用户认证、SSE、异步 Runs API、历史分页、Cron 管理界面和多节点租约。飞书 SDK/签名校验、Cron 持久化、工具级取消、后台 Subagent、完整远端 Terminal Backend 与真实模型复盘也需要按实际部署环境继续实现。

## 已实现模块

下面是发布核对时使用的完整类索引。第一次阅读不需要逐项展开，先按阶段 README 的 5～6 个核心文件学习即可。

<details>
<summary>展开完整模块索引</summary>

- `com.ading.ai.hermes.RuntimeIdentity`：项目身份。
- `com.ading.ai.hermes.runtime.RuntimeBoundaryMap`：Hermes 风格 Runtime 的七条核心边界。
- `com.ading.ai.hermes.core.AgentLoop`：受预算约束的最小 Agent Main Loop。
- `com.ading.ai.hermes.core.ErrorRecoveringAgentLoop`：统一处理停止信号、模型异常、工具系统异常和恢复预算；业务拒绝仍交给模型修正。
- `com.ading.ai.hermes.core.InterruptibleAgentLoop`：兼容旧入口的轻量委托器，实际执行统一交给 `ErrorRecoveringAgentLoop`。
- `com.ading.ai.hermes.core.TurnState`：一轮任务的事件、模型轮次、pending tool calls 和结束原因。
- `com.ading.ai.hermes.prompt.PromptBuilder`：把系统规则、运行事件、工具规格和模型参数组装成 `ChatRequest`。
- `com.ading.ai.hermes.prompt.PromptPlan`：按 STABLE、CONTEXT、VOLATILE 固定 Section 顺序，并生成稳定前缀指纹；默认兼容 Provider 不发送厂商私有缓存字段。
- `com.ading.ai.hermes.context.ContextFileCollector`：从工作区内按显式路径收集上下文文件，并记录被拒绝的路径和原因。
- `com.ading.ai.hermes.context.ContextEngine`：为当前一轮选择上下文的稳定接口。
- `com.ading.ai.hermes.context.CompactingContextEngine`：委托现有 Compactor 生成本轮上下文，并通过 Observer 暴露压缩结果。
- `com.ading.ai.hermes.context.ContextCompactor`：在上下文超过字符预算时保留头部任务和尾部现场，把中间历史压成带边界的摘要事件。
- `com.ading.ai.hermes.session.FileSessionStore`：把 `AgentEvent` 按 sessionId 追加写入 JSONL，并按顺序读回。
- `com.ading.ai.hermes.session.SqliteSessionStore`：使用 schema version、WAL、写冲突抖动重试、被动 checkpoint 与 FTS5 trigram 持久化 Session，支持 lineage、运行配置及中文检索。
- `com.ading.ai.hermes.runtime.HermesProfile`：把 Session、Memory、Skills 与 Trajectory 隔离到工作区内的独立状态目录。
- `com.ading.ai.hermes.session.SessionRestorer`：从持久化事件恢复 `AgentState`，判断 session 可继续、待工具观察或已完成。
- `com.ading.ai.hermes.session.RunCheckpointPlanner`：把 `SessionRecord` 转成恢复 checkpoint，保留 lastEventIndex、恢复决策和全部 pending tool requests。
- `com.ading.ai.hermes.session.SessionSearchIndex`：从多个 `SessionRecord` 中按关键词查回历史事件，返回 sessionId、事件位置、事件类型和命中片段。
- `com.ading.ai.hermes.memory.MemoryPolicy`：判断候选信息能否进入长期记忆，并给出目标、归一化内容和拒绝原因。
- `com.ading.ai.hermes.memory.MemoryStore`：拒绝重复或超过容量的记忆；配置存储目录后使用原子替换持久化项目与用户 Memory。
- `com.ading.ai.hermes.skill.SkillLoader`：从 skill 目录读取 `SKILL.md`，解析轻量元信息和过程正文。
- `com.ading.ai.hermes.skill.SkillResolver`：按当前任务匹配启用的 skill，避免无关技能进入上下文。
- `com.ading.ai.hermes.skill.SkillProvenance`：记录 skill 来源类型、来源标识和内容指纹。
- `com.ading.ai.hermes.skill.TrustedSkillPolicy`：把 skill 来源和启用状态转换成自动加载、暂存或阻断决策。
- `com.ading.ai.hermes.skill.SkillCandidateGenerator`：从任务复盘中生成待审 skill 候选。
- `com.ading.ai.hermes.skill.SkillApprovalFlow`：候选先持久化进入 pending，批准后才写出可加载 `SKILL.md`，重启后继续保留审批状态。
- `com.ading.ai.hermes.skill.TrajectorySelfImprovementReviewer`：从 `TrajectoryRecord` 中提取 Memory 候选和 Skill Candidate，只有恢复后的失败才进入技能候选生成。
- `com.ading.ai.hermes.skill.SelfImprovementLoop`：把 Memory 候选交给 `MemoryStore`，把 Skill Candidate 交给 `SkillApprovalFlow` 的 pending 队列。
- `com.ading.ai.hermes.skill.ReviewToolPolicy`：表达复盘阶段只允许 memory / skills 工具的白名单边界。
- `com.ading.ai.hermes.learning.LearningGraph`：构建 Memory、Skill 与可解释关系的不可变快照，同时报告悬空引用和自引用。
- `com.ading.ai.hermes.learning.LearningGraphMutations`：在不可变副本上执行写入，关系诊断不为空时原子拒绝整次修改。
- `com.ading.ai.hermes.core.AgentRuntime`：入口层、调度器和后续控制台共同依赖的运行时调用契约。
- `com.ading.ai.hermes.cli.JavaHermesCli`：解析 prompt 与最大轮次，调用 `AgentRuntime`，用稳定退出码表达成功、运行失败与配置错误。
- `com.ading.ai.hermes.cli.JavaHermesApplication`：加载本地配置或交互输入，注册工作区工具并组装可执行 CLI。
- `com.ading.ai.hermes.gateway.HttpGatewayHandler`：校验最小 HTTP envelope，把入口请求转换成 `AgentRunRequest`，再把 Runtime 结果封装成 Gateway 响应。
- `com.ading.ai.hermes.gateway.GatewayTurnRequest`：把 CLI、HTTP、消息平台等入口归一成 source、conversationId、userMessage 和 metadata。
- `com.ading.ai.hermes.gateway.GatewayAccessPolicy`：按频道放行、用户白名单、已配对主体、配对要求与全局兜底的固定顺序返回类型化鉴权决策。
- `com.ading.ai.hermes.gateway.GatewaySessionRouter`：从平台、聊天类型和聊天 ID 生成经过分段编码的稳定 Session Key。
- `com.ading.ai.hermes.gateway.feishu.FeishuEventHandler`：处理 Challenge、文本校验、身份鉴权、事件去重、稳定会话映射、Runtime 调用与回复投递；失败事件允许重试。
- `com.ading.ai.hermes.acp.HermesAcpAgent`：基于官方 ACP Java SDK 实现初始化、Session 新建/加载/恢复/分叉/列表、模型配置、Prompt、工具更新与取消映射。
- `com.ading.ai.hermes.acp.AcpApplication`：从本地私有配置装配 Runtime，并通过标准输入输出等待 Agent Client 连接。
- `com.ading.ai.hermes.gateway.local.LocalServiceRegistry`：按稳定名称注册类型化本地服务，拒绝重复服务名和错误请求类型。
- `com.ading.ai.hermes.gateway.local.FeishuLocalService`：把 `FeishuEventHandler` 注册为 `feishu.events`，服务容器不复制 Runtime 逻辑。
- `com.ading.ai.hermes.scheduler.CronScheduler`：以计划时间生成稳定 fire key；运行失败释放 claim，运行成功后独立记录投递成功或失败，并支持只重投结果。
- `com.ading.ai.hermes.control.FileEmergencyStop`：用原子更新的工作区哨兵暂停 HTTP、飞书与 Cron 新工作；损坏哨兵按失败关闭处理，不终止已经运行的任务。
- `com.ading.ai.hermes.scheduler.CronJob`：描述定时任务的 id、name、prompt、schedule、nextRunAt、deliveryTarget 和暂停状态。
- `com.ading.ai.hermes.scheduler.CronRunRecord`：记录一次 Cron 执行的 runId、jobId、fireKey、firedAt、nextRunAt、finalAnswer 和 finishReason。
- `com.ading.ai.hermes.delegate.SubAgentRunner`：为每个子任务创建独立会话，传入父停止信号，按 Toolset 收窄真实工具权限，只把摘要与结构化状态合并回父级；当前按顺序执行。
- `com.ading.ai.hermes.delegate.SubAgentTask`：描述子任务 id、goal、context、toolsets 和独立预算。
- `com.ading.ai.hermes.observability.TrajectoryRecorder`：把运行事件和子 Agent 结果转换成带 sessionId、turnId、taskId 和 parentTurnId 的轨迹事件。
- `com.ading.ai.hermes.observability.TraceRedactor`：在轨迹落盘前脱敏 apiKey、token、password、Bearer header 和 `sk-` token。
- `com.ading.ai.hermes.observability.FileTrajectoryStore`：把 `TrajectoryRecord` 追加写入 JSONL 文件。
- `com.ading.ai.hermes.model.ModelProvider`：模型调用边界。
- `com.ading.ai.hermes.provider.ProviderRuntimeResolver`：按请求覆盖、环境变量、Provider Profile 与默认值解析运行配置，隔离 Provider 凭证，并保留 Chat Completions、Codex Responses 和 Anthropic Messages 三种 API 模式。
- `com.ading.ai.hermes.model.ModelProviderDriver`：把 `ModelProvider` 接回 Main Loop 的适配器。
- `com.ading.ai.hermes.model.ScriptedModelProvider`：只供自动化测试固定协议边界，不用于六个阶段 Main 或读者运行结果。
- `com.ading.ai.hermes.model.OpenAiCompatibleModelProvider`：调用 OpenAI-compatible `/v1/chat/completions` 接口，解析文本、工具调用、原生 reasoning 和 usage。
- `com.ading.ai.hermes.metrics.MeteredModelProvider`：透明记录 Provider、Usage、耗时与结果；指标 Sink 失败时保留原模型响应或原始异常，并单独报告丢失指标。
- `com.ading.ai.hermes.metrics.InMemoryModelMetrics`：保存模型调用指标快照，供测试和最小观测入口使用。
- `com.ading.ai.hermes.model.ToolCallParser`：把原始 tool call 解析成请求、修复记录和错误报告。
- `com.ading.ai.hermes.tool.ToolRegistry`：按工具名把 `ToolRequest` 分发到 Java 执行器。
- `com.ading.ai.hermes.tool.ToolSchema`：在工具执行前校验必填参数和参数类型。
- `com.ading.ai.hermes.tool.ToolBatchRunner`：并发执行一组工具请求，按请求顺序回传 Observation，并把工具异常转成失败 Observation。
- `com.ading.ai.hermes.security.GuardedToolDriver`：在真实工具执行前运行安全策略，把需要人工确认或不允许的请求转成失败 Observation。
- `com.ading.ai.hermes.tools.basic.WorkspaceFileTools`：在工作区边界内读取 UTF-8 文件、列出目录，并把越界、类型错误和超大文件转成失败 Observation。
- `com.ading.ai.hermes.tools.basic.UniqueTextEdit`：只允许唯一匹配文本替换，零匹配和多匹配都拒绝修改。
- `com.ading.ai.hermes.tools.basic.WorkspaceEditTool`：把唯一匹配编辑能力注册为受工作区约束的 Runtime 工具。
- `com.ading.ai.hermes.context.reference.ContextReferenceResolver`：解析并展开 `@file`、`@folder`、`@diff`、`@staged`、`@git` 与 `@url`，执行路径和字符预算检查。
- `com.ading.ai.hermes.toolset.ToolsetCatalog`：统一管理工具归属、入口选择和名称唯一性。
- `com.ading.ai.hermes.toolset.McpToolAdapter`：把 MCP 工具按来源前缀、include/exclude 与冲突规则注册到 Toolset Catalog。
- `com.ading.ai.hermes.programmatic.ProgrammaticToolRuntime`：在允许工具、调用次数、超时和输出上限内执行多步工具程序，只返回聚合结果。
- `com.ading.ai.hermes.checkpoint.FileWorkspaceCheckpointStore`：为显式文件建立持久化基线，报告创建、修改、删除并支持保守回滚。
- `com.ading.ai.hermes.terminal.LocalProcessTerminalBackend`：以 argv 执行本地进程，限制工作目录与环境变量，处理超时、进程树和输出截断。
- `com.ading.ai.hermes.run.InMemoryRunCoordinator`：管理 Active Run、增量事件、审批、Queue、Steer 与 Interrupt 状态。
- `com.ading.ai.hermes.hook.RuntimeHookChain`：按优先级执行稳定 Hook，区分失败开放与失败关闭。
- `com.ading.ai.hermes.plugin.PluginHost`：让插件通过窄 Context 注册工具和 Hook，不直接改写 Main Loop。
- `com.ading.ai.hermes.harness.AgentHarness`：按 Context Reference、Hook、Workspace Checkpoint、AgentRuntime 与 Run 收口顺序完成总体编排。
- `com.ading.ai.hermes.runtime.HermesRuntimeFactory`：创建唯一 Harness 主链，统一装配 Provider Metrics、工具 Guardrails、Session、Trajectory、Memory、Skill 审批与飞书本地服务。
- `com.ading.ai.hermes.runtime.HermesRuntimeOptions`：把显式项目/用户 Memory、附加系统规则、按需 Skills、上下文预算和文件编辑权限带入工厂装配。
- `com.ading.ai.hermes.web.HermesWebApplication`：启动只监听本机回环地址的 Web Console，支持端口与默认工作区配置。
- `com.ading.ai.hermes.web.HermesWebServer`：提供模型/运行时配置、真实任务运行、最近轨迹、运行指标、Session Search 与 Skill 审批 API。
- `com.ading.ai.hermes.web.WebRuntimeConfig`：校验 Base URL、API Key、模型与工作区，API Key 只保存在当前 Java 进程内存中。
- `com.ading.ai.hermes.web.WebRuntimeSettings`：校验 Memory、Skills 目录和工具权限，限制 Skills 目录不能越过工作区，并转换为 `HermesRuntimeOptions`。
- `com.ading.ai.hermes.verification.CompletionGate`：只在最终回答后调用验证器，以结构化证据决定接受或拒绝，不擅自重跑 Agent。
- `com.ading.ai.hermes.verification.JavaProjectVerificationDetector`：识别 Maven、Gradle 与 Wrapper，把项目根、命令 argv 和探测证据固化为验证配方。
- `com.ading.ai.hermes.verification.ProjectVerificationRunner`：在配方指定的项目根顺序执行受限验证命令，首个失败停止，并转换为完成证据。
- `com.ading.ai.hermes.eval.BenchmarkRunner`：运行正式 `AgentRuntime`，由案例评估器依据结构化证据打分并聚合报告。
- `com.ading.ai.hermes.examples.coding.CodingAgentWorkflow`：把上下文收集、结构化模型计划、路径检查、原文匹配、验证命令白名单和 Trajectory 记录串成一次最小改代码任务。
- `com.ading.ai.hermes.examples.coding.CodingAgentPolicy`：约束每个上下文文件字符预算和允许执行的验证命令前缀。
- `com.ading.ai.hermes.examples.coding.VerificationRunner`：把测试命令执行抽象成可替换接口，当前测试使用记录型 verifier 固定 Runtime 行为。
- `web-console/index.html`：真实 Web Console 的页面入口。
- `web-console/app.js`：调用本地配置与运行 API，展示最终回答、Tool Request 和 Observation。
- `web-console/verify-console.mjs`：检查页面结构、API 接线和禁止浏览器持久化凭证等约束。
- `docs/runtime-boundary.md`：Runtime 边界说明。

</details>

## 读者运行

用 IDEA 打开当前项目，把 Project SDK 设为 JDK 21 或更高版本，然后选择 CLI 或 Web 入口。两种入口都会访问真实 OpenAI-compatible 模型，并调用同一套 `HermesRuntimeFactory`。

JDK 21 是项目基线，不支持 JDK 8。Runtime 使用了 `record`、标准 HTTP Client、不可变集合工厂和虚拟线程；这些能力直接服务状态建模、模型请求与并发工具执行。为兼容 JDK 8 替换这些语言和标准库能力，会让示例代码被兼容层淹没。

### 本地配置

最省事的方式是把 `config/hermes.local.properties.example` 另存为 `config/hermes.local.properties`，填写自己的模型配置：

```properties
openai.base-url=https://your-openai-compatible-endpoint
openai.api-key=你的_API_Key
openai.model=支持_Tool_Call_的模型名

# 可选
# hermes.workspace=.
# hermes.web.port=8080
# hermes.profile=default
```

真实配置文件已加入 `.gitignore`。不要删除这条忽略规则，也不要提交包含 API Key 的文件。全部阶段 Main 必须从这份本地文件读取完整配置；根项目优先采用文件中已填写的值，环境变量只补齐缺项，CLI 最后才询问仍然缺失的字段，Web 还允许启动后在页面修改。若同名环境变量与本地值冲突，启动日志只提示被忽略的变量名，不输出凭证值。

这里没有使用 `application.yml`：当前项目是纯 Java 应用，不会自动读取 Spring Boot 配置。用 JDK 自带的 `Properties` 可以保持启动链简单，也避免让读者误以为 Runtime 依赖 Spring。

### 入口一：CLI

直接运行 `JavaHermesApplication.main()`。如果本地配置尚未填写，CLI 会依次询问 Base URL、API Key 和模型；没有 Program arguments 时，还会继续询问任务。配置只在当前进程内使用，不会自动写入文件。

需要固定运行参数时，创建 `JavaHermesApplication` 的 Run Configuration：

- Main class：`com.ading.ai.hermes.cli.JavaHermesApplication`
- Working directory：保持为当前项目目录；需要操作其他练习目录时，在本地配置中设置 `hermes.workspace`
- Environment variables：可选；`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_MODEL` 只补齐本地文件中没有填写的字段
- Program arguments：可选；例如 `--prompt "读取 README.md，概括这个项目解决的问题" --max-turns 8 --session reader-session`

在系统终端运行时，API Key 输入会隐藏；IDEA Run Console 通常无法隐藏输入，因此在 IDEA 中更适合使用已忽略的本地配置。任务完成后，终端会输出 Session id、最终回答、结束原因和模型轮次。不传 `--session` 时每次运行生成新 Session；固定 Session id 后，可以在 Web 的运行状态页检索多次运行留下的证据。把任务改为读取或编辑工作区文件，可以继续观察 `read_file`、`list_directory` 与 `edit_file` 的真实工具闭环。

### 入口二：Web Console

直接运行 `com.ading.ai.hermes.web.HermesWebApplication.main()`，不需要提前填写模型配置。然后访问启动日志打印的地址，默认是：

```text
http://127.0.0.1:8080
```

第一次打开时，在“模型配置”页填写 Base URL、API Key、支持 Tool Call 的模型名和工作区。然后进入“运行时配置”，填写附加系统规则、项目记忆、用户记忆和工作区内的 Skills 目录。文件编辑默认关闭，只有读者主动开启后才注册 `edit_file`。保存运行时配置会通过 `HermesRuntimeFactory` 重新装配 Runtime，清空上一条轨迹；页面列出的 Skill 来自真实 `SKILL.md`，不是预置标签。

运行时配置还可以选择 Runtime Profile。不同 Profile 分别保存 Session、Memory、Skills 和 Trajectory，适合在同一工作区隔离日常编码与试验状态；Profile 不是多人权限或租户安全边界。

项目提供 `examples/skills/reader-summary/SKILL.md`。把 Skills 目录设置为 `examples/skills`，提交包含“总结”或“概括”的读取任务，即可验证 Skill 按需进入模型上下文。“运行轨迹”会展示真实的最终回答、模型轮次、Tool Request 和 Observation；刷新页面后仍能取回当前 Java 进程内最近一次运行。

Web 服务只监听 `127.0.0.1`。API Key 只保存在 Java 进程内存中，不会返回浏览器、不写入浏览器存储，也不由页面写入项目文件。页面手工填写的附加规则和初始 Memory 是进程内配置；Runtime 在运行中提取的 Memory、Session、Trajectory 和 Skill 审批状态会写入工作区的 `.hermes/`，服务重启后仍可读取。默认 `8080` 被占用时，应用会自动选择空闲端口并打印实际地址；如果显式设置了 `hermes.web.port` 或 `HERMES_WEB_PORT`，端口冲突时会提示修改该配置。

`OPENAI_BASE_URL` 可以是官方 OpenAI 地址，也可以是兼容 `/v1/chat/completions` 的服务地址。模型必须支持 Tool Call。不要把 API Key 写进 `pom.xml`、源码、测试资源或启动脚本。

### 入口三：ACP Agent

在支持 ACP 的 Agent Client 中配置 Java 启动项，由客户端启动 `com.ading.ai.hermes.acp.AcpApplication.main()`。该 Main 通过标准输入输出接收协议消息；它不是交互式控制台，直接在 IDEA Console 输入自然语言不会触发任务。

编辑器启动子进程时的工作目录可能不同于项目目录，因此 ACP 入口支持 `--config <绝对路径>`。Zed 配置样例见 [`config/zed-agent-settings.example.json`](config/zed-agent-settings.example.json)：替换项目绝对路径，在 IDEA 的 Maven 工具窗口执行 `package` 后，把该配置加入 Zed 的 `agent_servers`。密钥仍只写在已被 Git 忽略的 `hermes.local.properties`，不要放进编辑器设置。若图形界面进程找不到 `java`，把 `command` 改为当前 JDK 的 `bin/java` 或 `bin/java.exe` 绝对路径。

当前 ACP 入口支持协议版本 1、Session 新建/加载/恢复/分叉/列表、模型配置、文本 Prompt、工具状态与取消。Session 级 MCP、额外 workspace root、非文本内容、运行中的低延迟事件推送和可恢复审批桥尚未启用，收到相应请求时会明确拒绝，不会静默忽略。

## 运行链路

`JavaHermesApplication` 调用 `HermesRuntimeFactory` 完成进程内装配，CLI 使用 `assembly.runtime()`；`HermesWebApplication` 通过本地 HTTP Server 调用同一装配入口；`AcpApplication` 使用 `assembly.acp()`。工厂同时通过 `LocalServiceRegistry` 注册飞书 Handler。网络、消息平台和 ACP Transport 只负责协议传输，不在回调中复制 Runtime。

## 参考

- Hermes Agent 官方仓库：<https://github.com/NousResearch/hermes-agent>
- Hermes Agent 官方文档：<https://hermes-agent.nousresearch.com/docs/>
