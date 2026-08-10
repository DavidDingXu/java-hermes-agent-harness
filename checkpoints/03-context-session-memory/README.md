# 阶段 03：上下文、会话与学习状态

对应第 11～17 篇。这个快照把“当前轮该看什么”“跨轮保存什么”“长期学习什么”拆成 Context、Session、Memory、Skill 与 Learning Graph 五条边界。

## 先读这 6 个文件

1. `prompt/PromptPlan.java`：稳定、上下文、易变三层提示词。
2. `context/ContextCompactor.java`：超长历史的保留规则。
3. `session/SqliteSessionStore.java`：隔离、持久化与检索。
4. `memory/MemoryStore.java`：受策略约束的长期记忆。
5. `skill/SkillResolver.java`：按任务加载 Skill。
6. `learning/LearningGraphMutations.java`：关系诊断与原子更新。

## 运行验收

直接运行 `StateCheckpointApplication.main()`。程序会先建立真实 Session、Memory、Skill 与压缩摘要，再要求真实模型同时使用这些证据回答。最终回答必须包含 Java 21、`pom.xml` 和“先运行聚焦验证”。

下一阶段会为这套状态系统加入恢复、安全策略和多端入口。
