# Java Hermes Agent Harness Roadmap

## Phase 1: 最小 Agent Harness

- `runtime`: Runtime 边界模型。
- `agent-core`: Main Loop、Turn State、终止条件。
- `agent-model`: ModelProvider、OpenAI-compatible Provider、原生 Reasoning、Tool Call 解析与兼容边界已实现。

## Phase 2: 工具与执行安全

- `agent-tool`: Tool Registry、ToolResult、参数校验已实现。
- `agent-tools-basic`: 工作区文件读取、目录列表和唯一匹配文本编辑已实现；面向模型的通用搜索与 Shell Tool 待实现。
- `agent-tool`: 并发工具批处理已实现；资源冲突检测待实现。
- `agent-toolset`: Toolset 归属与入口选择、MCP 动态工具适配、include/exclude 和名称冲突失败关闭已实现；MCP 网络传输与 Server 生命周期管理待实现。
- `agent-programmatic`: 允许工具、最大调用次数、超时与输出上限约束的程序化工具运行时已实现；外部语言沙箱与进程 RPC 待实现。
- `agent-terminal`: 跨平台本地进程后端、工作目录校验、环境白名单、超时进程树清理和输出截断已实现；Docker、SSH 和云端沙箱后端待实现。
- `agent-checkpoint`: 显式文件清单、持久化 Manifest、Diff 和保守 Rollback 已实现；原子快照、摘要冲突检查和元数据恢复待实现。
- `agent-security`: 工具安全策略网关已实现；高危命令拦截细化和人工审批待实现。
- `agent-verification`: 最终回答后的结构化验证门禁已实现；生产验证策略与审批联动待实现。

## Phase 3: 上下文与会话工程

- `agent-prompt`: System Prompt 和运行事件上下文组装已实现。
- `agent-context`: Context Files 安全收集、ContextEngine 接口和字符预算版 Context Compaction 已实现；模型级 Token 预算待实现。
- `agent-context-reference`: `@file`、`@folder`、`@diff`、`@staged`、`@git` 与 `@url` 解析、路径保护和硬字符预算已实现；URL 网络策略、内容类型检查和 Token 预算待实现。
- `agent-session`: 文件 Session、SQLite schema version、WAL、FTS5 中文检索与恢复判断已实现，默认 CLI/Web 主链已写入 SQLite；跨进程租约与分页浏览待实现。
- `agent-core`: 错误事件化和恢复预算已实现；错误分类、退避和 provider fallback 待实现。

## Phase 4: 记忆与技能

- `agent-memory`: 长期记忆写入条件、重复/容量拦截、原子文件持久化与下一轮 Prompt 注入已实现；记忆版本、撤销和人工编辑界面待实现。
- `agent-session`: SQLite/FTS5 Session Search 已接入运行时和 Web 检索入口；scroll、browse 和结果分页待实现。
- `agent-skill`: Skills 加载、任务匹配、Provenance、信任策略、候选生成、pending/批准文件持久化和 Web 人工确认已实现；Skill diff 展示、版本回滚和复盘通知待实现。
- `agent-learning`: Memory、Skill、显式关系和可解释关联边的 Learning Graph 已实现；持久化 mutation、回滚和查询接口待实现。

## Phase 5: Cron、Gateway 与中断恢复

- `agent-gateway`: HTTP Gateway 契约与本地 Web Server 已实现；多用户认证、SSE、异步 Runs API 和通用审批续跑待实现。
- `agent-cli`: 可执行 CLI、环境变量配置和工作区工具装配已实现。
- `agent-local-service`: 类型化本地服务定义、注册表和飞书服务注册已实现；进程间传输和服务生命周期管理待实现。
- `agent-gateway-feishu`: Challenge、文本校验、事件去重、会话映射和回复投递核心已实现；真实飞书 SDK、签名校验和持久化幂等待实现。飞书处理器通过本地服务注册，不依附 HTTP Gateway。
- `agent-scheduler`: Cron Scheduler 最小调度内核已实现；自然语言 schedule 解析、任务持久化、跨进程锁、no-agent mode、skill-backed jobs、context_from 和平台投递待实现。
- `agent-core` / `agent-session`: 停止信号、可中断运行循环、`RUN_INTERRUPTED` 事件和恢复 checkpoint 已实现；工具级取消与跨进程恢复待实现。
- `agent-run`: 单 Session Active Run、增量事件、审批暂停/恢复、Queue、Steer 与 Interrupt 的内存状态机已实现；持久化 Run/Event、HTTP Runs API、SSE Adapter、租约和分布式一致性待实现。

## Phase 6: Subagent、Trajectory 与自进化边界

- `agent-delegate`: 子 Agent 上下文隔离、预算边界和结果合并已实现；后台 subagent、真实并发、工具集过滤、嵌套 orchestrator 和 parent/child session 记录待实现。
- `agent-observability`: Trajectory 记录、敏感字段脱敏、JSONL 持久化、模型 Token/耗时指标和 Web 运行状态展示已实现；API request id、streaming flush、轨迹索引和历史分页待实现。
- `agent-eval`: 基于正式 AgentRuntime、案例评估器和结构化证据的 Benchmark Runner 已实现；并发评测、持久化报告和统计显著性分析待实现。
- `agent-skill`: Trajectory 自进化复盘、Memory/Skill 分流、Skill pending 审批边界和 review 工具白名单模型已实现；后台复盘线程、真实模型复盘、工具分发白名单接入和复盘通知待实现。

## Phase 7: 实战与收官

- `examples/coding-agent`: 上下文收集、结构化模型计划、路径检查、原文匹配、验证命令白名单、Trajectory 记录和可选真实 OpenAI-compatible 模型入口已实现；完整 diff parser、接入 Terminal Backend 的真实验证执行、git diff 展示、人工审批界面和控制台后端接口待实现。
- `web-console`: 本地 HTTP Server、模型/运行时配置、按需 Skills、文件编辑权限、任务提交、真实轨迹、运行指标、Session Search 和 Skill Approval 已实现；页面手工配置持久化、异步 Runs API、SSE、认证、历史分页和 Cron 管理界面待实现。
- 全项目 Runtime 边界复盘已完成。

## Phase 8: 完整 Agent Harness

- `agent-hook`: 有序 Hook Chain、payload 转换、阻止决定、warning、失败开放与失败关闭已实现；类型化事件 Schema 与持久化 Hook 版本待实现。
- `agent-plugin`: 通过窄 PluginContext 注册工具和 Hook 的不可变 PluginHost 已实现；插件来源校验、兼容范围和进程隔离待实现。
- `agent-harness`: Run 创建、Context References、BEFORE_RUN Hook、Workspace Checkpoint、AgentRuntime、AFTER_RUN Hook 和 Run 终态收口已实现；异步执行、审批续跑与持久化编排待实现。
