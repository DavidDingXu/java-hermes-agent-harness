# 阶段 04：恢复、安全与多端入口

对应第 18～26 篇。这个快照内容较多，因此按学习进度提供四个聚焦 Main，最后再运行一次累计总验收。

| 读完文章 | 直接运行 | 重点观察 |
| --- | --- | --- |
| 第 20 篇 | `RecoverySecurityCheckpointApplication.main()` | 恢复预算、失败类型与路径拦截 |
| 第 24 篇 | `ProtocolEntryCheckpointApplication.main()` | Gateway、飞书事件核心和 ACP 复用 Runtime |
| 第 25 篇 | `CronCheckpointApplication.main()` | 计划触发身份、下一次运行与投递状态 |
| 第 26 篇 | `SubAgentCheckpointApplication.main()` | 独立子会话、工具集和轮次预算 |
| 第 26 篇 | `GovernedEntryCheckpointApplication.main()` | 阶段 04 全链路累计验收 |

## 建议阅读顺序

先看 `core/ErrorRecoveringAgentLoop.java` 与 `security/GuardedToolDriver.java`，再看 `gateway/`、`acp/HermesAcpAgent.java`、`scheduler/CronScheduler.java`，最后看 `delegate/SubAgentRunner.java`。入口层只做协议转换，真正的运行逻辑仍由同一个 `AgentRuntime` 承担。

所有 Main 都读取项目根目录的本地私有配置并调用真实模型。SubAgent 当前按顺序执行子任务；已经实现会话、工具集、预算与父停止信号隔离，尚未实现后台并发子进程。
