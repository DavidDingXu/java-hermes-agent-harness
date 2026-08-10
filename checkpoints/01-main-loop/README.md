# 阶段 01：Main Loop 与模型协议

对应第 01～06 篇。这个快照只回答一个问题：模型如何在预算内完成“决策、调用工具、读取 Observation、给出最终回答”的闭环。

## 先读这 5 个文件

1. `core/AgentLoop.java`：循环骨架与退出条件。
2. `core/TurnState.java`：当前轮的状态机。
3. `core/TurnFinalizer.java`：最终回答与预算耗尽如何收口。
4. `model/ModelProviderDriver.java`：模型响应如何转成 `ModelTurn`。
5. `model/ToolCallParser.java`：不完整 Tool Call 如何修复或拒绝。

## 运行验收

直接运行 `MainLoopCheckpointApplication.main()`。程序会调用本地配置中的真实模型，要求模型先调用标记工具，再依据 Observation 回答。看到“Main Loop 运行成功”、两次以上模型调用和一条工具 Observation，说明闭环成立。

下一阶段会把临时工具替换成可注册、可校验、可并发的工作区工具。
