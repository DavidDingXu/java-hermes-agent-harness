# 阶段 02：工作区工具系统

对应第 07～10 篇与阶段作业。这个快照在 Main Loop 之上加入 Tool Registry、Schema 校验、工作区路径边界、唯一匹配编辑和批量执行。

## 先读这 5 个文件

1. `tool/ToolRegistry.java`：注册和分发工具。
2. `tool/ToolSchema.java`：执行前校验参数。
3. `tools/basic/WorkspaceFileTools.java`：读取文件与列目录。
4. `tools/basic/UniqueTextEdit.java`：零匹配、多匹配都拒绝写入。
5. `tool/ToolBatchRunner.java`：安全工具串行、独立读取并行。

## 运行验收

直接运行 `ToolRuntimeCheckpointApplication.main()`。真实模型必须完成 `read_file -> edit_file -> FINAL_ANSWER`，程序随后读取磁盘确认内容确实变化。看到“在线文件结果：状态：在线验证”才算通过。

下一阶段会处理长对话、持久会话、长期记忆和按需 Skill。
