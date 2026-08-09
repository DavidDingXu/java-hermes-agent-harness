package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.context.ContextCompactionPolicy;
import com.ading.ai.hermes.context.ContextCompactionResult;
import com.ading.ai.hermes.context.ContextCompactor;
import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.learning.LearningGraph;
import com.ading.ai.hermes.learning.LearningGraphSnapshot;
import com.ading.ai.hermes.learning.LearningMemory;
import com.ading.ai.hermes.learning.LearningSkill;
import com.ading.ai.hermes.memory.MemoryCandidate;
import com.ading.ai.hermes.memory.MemoryPolicy;
import com.ading.ai.hermes.memory.MemoryStore;
import com.ading.ai.hermes.memory.MemoryTarget;
import com.ading.ai.hermes.session.SessionId;
import com.ading.ai.hermes.session.SessionRestorer;
import com.ading.ai.hermes.session.SqliteSessionStore;
import com.ading.ai.hermes.skill.SkillLoader;
import com.ading.ai.hermes.skill.SkillManifest;
import com.ading.ai.hermes.skill.SkillResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class StateCheckpointApplication {

    private StateCheckpointApplication() {
    }

    public static void main(String[] args) throws Exception {
        ReaderModelRuntime readerModel = ReaderModelRuntime.fromLocalConfiguration();
        Path stateDirectory = Files.createTempDirectory("hermes-state-");
        try {
            SqliteSessionStore sessions = persistSession(stateDirectory);
            ContextCompactionResult compacted = compactContext(sessions);
            MemoryStore memories = persistMemory(stateDirectory);
            SkillManifest skill = loadSkill(stateDirectory);
            LearningGraphSnapshot graph = buildLearningGraph();

            require(compacted.compacted(), "Context 没有触发压缩");
            var searchHits = sessions.search("工作区", 5);
            require(!searchHits.isEmpty(), "Session Search 没有命中证据");
            require(searchHits.stream().allMatch(hit -> "reader-session".equals(hit.sessionId().value())),
                    "Session Search 返回了其他会话的证据");
            require(memories.entries(MemoryTarget.MEMORY).size() == 1, "Memory 没有跨实例恢复");
            require(new SkillResolver(List.of(skill)).resolve("请检查 maven 测试").size() == 1, "Skill 没有按需匹配");
            require(graph.edges().size() == 1, "Learning Graph 没有连接 Memory 与 Skill");

            String systemPrompt = """
                    你正在验证 Hermes 的状态层。下面的信息已通过 Memory 与 Skill 边界进入当前轮：
                    Memory: %s
                    Skill: %s
                    Session 历史与压缩摘要会作为同一条模型调用的对话历史传入。
                    请只依据这些信息回答，并使用简洁中文；回答必须明确包含 Java 21、pom.xml
                    和“先运行聚焦验证”这三个证据。
                    """.formatted(memories.entries(MemoryTarget.MEMORY), skill.instructions());
            AgentRunResult liveResult = readerModel.run(
                    systemPrompt,
                    "这个项目使用什么 Java 版本？执行 Java 测试时应先做什么？",
                    request -> ToolObservation.failure(request.callId(), "本轮不需要工具"),
                    List.of(),
                    4,
                    compacted.state()
            );
            require(liveResult.finishReason() == FinishReason.FINAL_ANSWER, "真实模型没有正常回答");
            require(liveResult.state().events().stream().anyMatch(event ->
                            event.kind() == com.ading.ai.hermes.core.AgentEventKind.CONTEXT_SUMMARY),
                    "压缩后的 Session 历史没有进入真实模型链路");
            require(liveResult.finalAnswer().contains("21")
                            && liveResult.finalAnswer().contains("pom.xml")
                            && liveResult.finalAnswer().contains("聚焦"),
                    "真实模型没有同时使用 Session、Memory 与 Skill 证据");

            System.out.println("[阶段 03] Context、Session、Memory 与 Skill 运行成功");
            System.out.println("Context 事件: " + compacted.report().originalEvents()
                    + " -> " + compacted.report().retainedEvents());
            System.out.println("SQLite journal_mode: " + sessions.journalMode());
            System.out.println("持久化 Memory: " + memories.entries(MemoryTarget.MEMORY));
            System.out.println("按需匹配 Skill: " + skill.name());
            System.out.println("Learning Graph 边数: " + graph.edges().size());
            System.out.println("真实模型: " + readerModel.model());
            System.out.println("在线状态注入回答: " + ReaderModelRuntime.preview(liveResult.finalAnswer()));
        } finally {
            deleteRecursively(stateDirectory);
        }
    }

    private static ContextCompactionResult compactContext(SqliteSessionStore sessions) {
        AgentState restored = new SessionRestorer()
                .restore(sessions.load(new SessionId("reader-session")))
                .state();
        return new ContextCompactor(new ContextCompactionPolicy(120, 1, 1, 180, 48))
                .compact(restored);
    }

    private static SqliteSessionStore persistSession(Path directory) {
        Path database = directory.resolve("sessions.db");
        SessionId sessionId = new SessionId("reader-session");
        SqliteSessionStore store = new SqliteSessionStore(database);
        store.append(sessionId, AgentEvent.userMessage("检查工作区路径校验和构建入口"));
        store.append(sessionId, AgentEvent.toolRequested(new ToolRequest(
                "call-session", "read_file", Map.of("path", "pom.xml")
        )));
        store.append(sessionId, AgentEvent.toolObserved(ToolObservation.success(
                "call-session",
                "已确认项目构建入口是 pom.xml。" + "workspace evidence ".repeat(12)
        )));
        store.append(sessionId, AgentEvent.modelFinalAnswer("工作区路径安全，构建入口是 pom.xml"));
        return new SqliteSessionStore(database);
    }

    private static MemoryStore persistMemory(Path directory) {
        Path memoryDirectory = directory.resolve("memory");
        MemoryStore store = new MemoryStore(MemoryPolicy.defaultPolicy(), 2_048, 2_048, memoryDirectory);
        store.consider(MemoryCandidate.fromObservation(
                "Project java-hermes-agent-harness uses Maven and Java 21."
        ));
        return new MemoryStore(MemoryPolicy.defaultPolicy(), 2_048, 2_048, memoryDirectory);
    }

    private static SkillManifest loadSkill(Path directory) throws Exception {
        Path skillDirectory = directory.resolve("skills/java-testing");
        Files.createDirectories(skillDirectory);
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
                ---
                name: java-testing
                description: Run focused Java tests
                version: 1.0.0
                enabled: true
                triggers: [maven, junit, 测试]
                ---

                # Java Testing

                先运行聚焦验证，再运行全量测试。
                """, StandardCharsets.UTF_8);
        return new SkillLoader().load(skillDirectory);
    }

    private static LearningGraphSnapshot buildLearningGraph() {
        return LearningGraph.build(
                List.of(new LearningMemory("memory-1", "java hermes context compaction policy")),
                List.of(new LearningSkill(
                        "skill-1",
                        "context-compaction",
                        "java hermes context compaction workflow",
                        List.of()
                ))
        );
    }

    private static void deleteRecursively(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
