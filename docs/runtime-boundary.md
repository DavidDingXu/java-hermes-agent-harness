# Runtime Boundary

Agent Runtime 不是一次模型调用。最小 Hermes 风格 Runtime 至少要拆出七条边界：

| 边界 | 输入 | 输出 | 责任 |
|---|---|---|---|
| Entry | CLI、HTTP、定时任务或恢复请求 | 标准化 TurnRequest | 把不同入口收敛成同一种运行时请求 |
| Model | 消息、工具说明、模型参数、Provider 配置 | 模型消息、工具调用、用量或错误 | 屏蔽不同模型服务的协议差异 |
| Tool | 工具名、调用 ID、JSON 参数 | 结构化工具结果或错误 | 把模型意图转换成受控 Java 动作 |
| Context | 身份、环境、项目指令、工具、会话、记忆、技能 | 下一次模型调用的上下文 | 控制提示词来源、顺序、安全和长度 |
| Session | 消息、工具调用、观察结果、检查点 | 可恢复、可搜索的会话状态 | 支持续跑、回放、检索和问题定位 |
| Safety | 工具请求、路径、命令、上下文文件、变更建议 | 放行、拦截、审批请求或清洗结果 | 把模型意图放在明确权限边界后面 |
| Observability | 模型调用、工具调用、错误、用量、结果 | Trace、Trajectory、成本记录或复盘信号 | 让每次运行可解释、可调试、可评估 |

当前代码只实现边界模型：

- `RuntimeBoundary`
- `BoundaryKind`
- `RuntimeBoundaryMap`

它还没有实现 Main Loop、模型调用、工具执行、Session 存储和 Memory。这个阶段的目标是先把 Agent Runtime 的模块边界说清楚，并用测试固定下来。

运行验证：

```bash
mvn test
```

正常情况下，`RuntimeBoundaryMapTest` 会验证三件事：

- 七条边界按固定顺序存在。
- 每条边界都有输入、输出、责任和 Hermes 证据。
- 同一种边界不能重复注册。
