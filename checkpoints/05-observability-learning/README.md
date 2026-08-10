# 阶段 05：观测、评估与受控学习

对应第 27～31 篇。这个快照让一次真实运行留下可回答问题的证据，再把复盘建议送入受审批约束的 Memory 与 Skill 候选链路。

## 先读这 5 个文件

1. `metrics/MeteredModelProvider.java`：透明记录调用结果，指标故障不影响模型语义。
2. `observability/TrajectoryRecorder.java`：把运行事件转换成可复盘轨迹。
3. `eval/BenchmarkRunner.java`：按结构化证据比较 Runtime 行为。
4. `skill/SelfImprovementLoop.java`：分流 Memory 与 Skill 候选。
5. `context/reference/ContextReferenceResolver.java`：安全展开文件、目录和 Git 证据。

## 运行验收

直接运行 `ObservabilityCheckpointApplication.main()`。程序会调用真实模型，并检查 Metrics、Trajectory、Benchmark 和待审候选是否都由这次运行产生。指标落盘失败不会改变模型原始成功或失败结果。

下一阶段会加入 Toolset、MCP、检查点、终端、Run 生命周期与扩展机制。
