# Runtime Boundary

Agent Runtime 承担的是一次任务的完整生命周期，不是一次模型请求。Hermes 风格的 Runtime 至少要分开七条边界：

| 边界 | 输入 | 输出 | 责任 |
| --- | --- | --- | --- |
| Entry | CLI、HTTP、定时任务或消息事件 | `AgentRunRequest` | 统一任务身份、会话身份、轮次预算与元数据 |
| Model | 消息、工具说明、模型参数与 Provider 配置 | 最终回答、Tool Call、Reasoning、Usage 或错误 | 隔离模型协议差异，不执行工具 |
| Tool | 工具名、call id 与结构化参数 | `ToolObservation` | 在 Schema、权限、路径和并发边界内执行 Java 动作 |
| Context | 项目规则、当前任务、会话事件、Memory、Skills 与显式引用 | 下一次模型调用的可见上下文 | 决定信息来源、顺序、长度与信任级别 |
| Session | 用户消息、Tool Call、Observation、错误与终态 | 可回放、可检索的事件序列 | 保留事实历史，支持问题定位和会话检索 |
| Safety | 工具请求、工作区路径、上下文引用和学习候选 | 放行、拒绝、待审或脱敏结果 | 在副作用之前失败关闭，学习变更经过明确审批 |
| Observability | 模型调用、工具调用、错误、Usage 与结果 | Metrics、Trajectory、Benchmark 与复盘信号 | 让运行可解释、可评估，并为受治理的学习提供证据 |

`RuntimeBoundaryMap` 固定七条边界的顺序和责任；具体执行由 `HermesRuntimeFactory` 装配。

## 默认主链

CLI、Web 和注册到 Local Service 的飞书事件最终都进入同一个 `AgentRuntime`：

```text
Entry
  -> AgentHarness
     -> ContextReferenceResolver
     -> BEFORE_RUN Hooks
     -> WorkspaceCheckpointStore（按需）
     -> InterruptibleAgentLoop
        -> Metered ModelProvider
        -> Guarded ToolBatchRunner
     -> AFTER_RUN Hooks
     -> RunCoordinator 终态
  -> HermesRuntimeState
     -> SQLite Session
     -> JSONL Trajectory
     -> Memory Policy / Store
     -> Skill Candidate Approval
```

`AgentHarness` 拥有任务级编排，`InterruptibleAgentLoop` 只拥有模型与工具轮转。Provider 不能直接调工具，工具不能修改 Session 生命周期，复盘逻辑也不能绕过审批流程使 Skill 生效。

## 持久化状态

默认工厂在当前工作区的 `.hermes/` 下管理运行状态：

```text
.hermes/
├── sessions.db
├── trajectories.jsonl
├── memory/
│   ├── project-memory.json
│   └── user-memory.json
└── skills/
    ├── .pending/
    └── approved-skill-candidate-*/SKILL.md
```

写入 Session 和 Trajectory 前会脱敏 API Key、Bearer Token 和常见凭证字段。`.hermes/`、`.git/`、`.env*` 与 `config/hermes.local.properties` 同时被工作区文件工具隐藏和拒绝，避免 Agent 通过普通读文件路径拿到自己的凭证或内部状态。

## 如何核对边界

学习过程中可直接运行 `checkpoints/` 下的 6 个 Main。它们从模型循环逐步增长到高级 Harness，每个目录都保留当时的独立源码。

最终装配通过 `JavaHermesApplication.main()` 或 `HermesWebApplication.main()` 验证。读者配置好 OpenAI-compatible Base URL、API Key 和支持 Tool Call 的模型后，就能运行真实文件读写任务，并在 Web 的“运行状态”页查看持久化证据。

当前主链是本地单进程实现。它证明上述边界能够协同工作，不代替多租户认证、分布式租约、密钥托管、远端沙箱和生产审计等部署能力。
