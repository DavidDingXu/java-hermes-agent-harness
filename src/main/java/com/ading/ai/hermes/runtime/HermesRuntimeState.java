package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.memory.MemoryPolicy;
import com.ading.ai.hermes.memory.MemoryStore;
import com.ading.ai.hermes.metrics.InMemoryModelMetrics;
import com.ading.ai.hermes.learning.LearningGraph;
import com.ading.ai.hermes.learning.LearningGraphSnapshot;
import com.ading.ai.hermes.learning.LearningMemory;
import com.ading.ai.hermes.learning.LearningSkill;
import com.ading.ai.hermes.observability.FileTrajectoryStore;
import com.ading.ai.hermes.observability.TraceRedactor;
import com.ading.ai.hermes.observability.TrajectoryRecord;
import com.ading.ai.hermes.observability.TrajectoryRecorder;
import com.ading.ai.hermes.session.SessionId;
import com.ading.ai.hermes.session.SessionRestorer;
import com.ading.ai.hermes.session.SqliteSessionStore;
import com.ading.ai.hermes.skill.SelfImprovementLoop;
import com.ading.ai.hermes.skill.SelfImprovementResult;
import com.ading.ai.hermes.skill.SkillApprovalFlow;
import com.ading.ai.hermes.skill.SkillCandidateGenerator;
import com.ading.ai.hermes.skill.TrajectorySelfImprovementReviewer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class HermesRuntimeState {

    private static final int MEMORY_CHARACTER_LIMIT = 40_000;

    private final SqliteSessionStore sessions;
    private final FileTrajectoryStore trajectories;
    private final InMemoryModelMetrics metrics;
    private final MemoryStore memories;
    private final SkillApprovalFlow skillApprovals;
    private final TrajectoryRecorder trajectoryRecorder;
    private final SelfImprovementLoop selfImprovement;
    private final TraceRedactor redactor;
    private final AtomicReference<RuntimeRunArtifacts> latest = new AtomicReference<>();

    public HermesRuntimeState(Path workspace) {
        this(workspace, HermesProfile.defaultProfile());
    }

    public HermesRuntimeState(Path workspace, HermesProfile profile) {
        Path stateDirectory = Objects.requireNonNull(profile, "profile must not be null")
                .stateDirectory(workspace);
        ObjectMapper objectMapper = new ObjectMapper();
        this.sessions = new SqliteSessionStore(stateDirectory.resolve("sessions.db"));
        this.trajectories = new FileTrajectoryStore(
                stateDirectory.resolve("trajectories.jsonl"),
                objectMapper
        );
        this.metrics = new InMemoryModelMetrics();
        this.memories = new MemoryStore(
                MemoryPolicy.defaultPolicy(),
                MEMORY_CHARACTER_LIMIT,
                MEMORY_CHARACTER_LIMIT,
                stateDirectory.resolve("memory")
        );
        this.skillApprovals = new SkillApprovalFlow(stateDirectory.resolve("skills"));
        this.redactor = new TraceRedactor();
        this.trajectoryRecorder = new TrajectoryRecorder(Clock.systemUTC(), redactor);
        this.selfImprovement = new SelfImprovementLoop(
                new TrajectorySelfImprovementReviewer(new SkillCandidateGenerator()),
                memories,
                skillApprovals
        );
    }

    public synchronized RuntimeRunArtifacts recordRun(
            AgentRunRequest request,
            AgentRunResult result
    ) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(result, "result must not be null");
        SessionId sessionId = sessionId(request.conversationId());
        if (sessions.lineage(sessionId).isEmpty()) {
            sessions.create(sessionId, request.source(), null);
        }
        result.state().events().stream()
                .map(this::redact)
                .forEach(event -> sessions.append(sessionId, event));
        String runId = request.metadata().getOrDefault(
                AgentHarnessMetadata.RUN_ID,
                "run-" + UUID.randomUUID()
        );
        TrajectoryRecord trajectory = trajectoryRecorder.recordRun(
                sessionId.value(),
                runId,
                result
        );
        trajectories.append(trajectory);
        SelfImprovementResult improvement = selfImprovement.review(trajectory);
        RuntimeRunArtifacts artifacts = new RuntimeRunArtifacts(trajectory, improvement);
        latest.set(artifacts);
        return artifacts;
    }

    public synchronized AgentState restoreConversation(String conversationId) {
        SessionId sessionId = sessionId(conversationId);
        var restored = new SessionRestorer().restore(sessions.load(sessionId));
        if (restored.pendingToolRequests().isEmpty()) {
            return new AgentState(restored.state().events(), 0);
        }

        var events = new java.util.ArrayList<>(restored.state().events());
        for (ToolRequest request : restored.pendingToolRequests()) {
            AgentEvent closure = AgentEvent.toolObserved(ToolObservation.failure(
                    request.callId(),
                    "previous run stopped before tool execution; the tool was not replayed automatically"
            ));
            sessions.append(sessionId, closure);
            events.add(closure);
        }
        return new AgentState(events, 0);
    }

    public SqliteSessionStore sessions() {
        return sessions;
    }

    public FileTrajectoryStore trajectories() {
        return trajectories;
    }

    public InMemoryModelMetrics metrics() {
        return metrics;
    }

    public MemoryStore memories() {
        return memories;
    }

    public SkillApprovalFlow skillApprovals() {
        return skillApprovals;
    }

    public Optional<RuntimeRunArtifacts> latest() {
        return Optional.ofNullable(latest.get());
    }

    public LearningGraphSnapshot learningGraph() {
        var learningMemories = java.util.stream.Stream.of(
                        memories.entries(com.ading.ai.hermes.memory.MemoryTarget.MEMORY),
                        memories.entries(com.ading.ai.hermes.memory.MemoryTarget.USER)
                )
                .flatMap(java.util.Collection::stream)
                .distinct()
                .map(content -> new LearningMemory(stableId("memory", content), content))
                .toList();
        var learningSkills = skillApprovals.approvedSkills().stream()
                .map(skill -> new LearningSkill(
                        stableId("skill", skill.name()),
                        skill.name(),
                        skill.description().isBlank() ? skill.instructions() : skill.description(),
                        java.util.List.of()
                ))
                .toList();
        return LearningGraph.build(learningMemories, learningSkills);
    }

    private SessionId sessionId(String raw) {
        try {
            return new SessionId(raw);
        } catch (IllegalArgumentException error) {
            UUID stable = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
            return new SessionId("session-" + stable);
        }
    }

    private String stableId(String kind, String value) {
        return kind + ":" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private AgentEvent redact(AgentEvent event) {
        AgentEventKind kind = event.kind();
        return switch (kind) {
            case USER_MESSAGE -> AgentEvent.userMessage(redactor.redact(event.text()));
            case CONTEXT_SUMMARY -> AgentEvent.contextSummary(redactor.redact(event.text()));
            case ERROR_RECOVERED -> AgentEvent.errorRecovered(redactor.redact(event.text()));
            case COMPLETION_REJECTED -> AgentEvent.completionRejected(redactor.redact(event.text()));
            case RUN_INTERRUPTED -> AgentEvent.runInterrupted(redactor.redact(event.text()));
            case MODEL_FINAL_ANSWER -> AgentEvent.modelFinalAnswer(redactor.redact(event.text()));
            case TOOL_REQUESTED -> AgentEvent.toolRequested(redact(event.toolRequest()));
            case TOOL_OBSERVED -> AgentEvent.toolObserved(new ToolObservation(
                    event.toolObservation().callId(),
                    event.toolObservation().success(),
                    redactor.redact(event.toolObservation().content()),
                    event.toolObservation().failureKind()
            ));
        };
    }

    private ToolRequest redact(ToolRequest request) {
        return new ToolRequest(
                request.callId(),
                request.name(),
                redactor.redactMap(request.arguments())
        );
    }
}
