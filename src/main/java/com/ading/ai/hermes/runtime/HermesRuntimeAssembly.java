package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.acp.HermesAcpAgent;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.control.FileEmergencyStop;
import com.ading.ai.hermes.gateway.local.LocalServiceRegistry;
import com.ading.ai.hermes.harness.AgentHarness;
import com.ading.ai.hermes.run.RunCoordinator;
import com.ading.ai.hermes.memory.MemoryStore;
import com.ading.ai.hermes.metrics.InMemoryModelMetrics;
import com.ading.ai.hermes.observability.FileTrajectoryStore;
import com.ading.ai.hermes.session.SqliteSessionStore;
import com.ading.ai.hermes.skill.SkillApprovalFlow;
import com.ading.ai.hermes.tool.ToolRegistry;
import com.ading.ai.hermes.learning.LearningGraphSnapshot;
import java.util.Objects;

public record HermesRuntimeAssembly(
        AgentRuntime runtime,
        AgentHarness harness,
        RunCoordinator runs,
        ToolRegistry tools,
        LocalServiceRegistry localServices,
        FileEmergencyStop emergencyStop,
        HermesRuntimeState state,
        HermesAcpAgent acp
) {
    public HermesRuntimeAssembly {
        Objects.requireNonNull(runtime, "runtime must not be null");
        Objects.requireNonNull(harness, "harness must not be null");
        Objects.requireNonNull(runs, "runs must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(localServices, "localServices must not be null");
        Objects.requireNonNull(emergencyStop, "emergencyStop must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(acp, "acp must not be null");
    }

    public SqliteSessionStore sessions() {
        return state.sessions();
    }

    public FileTrajectoryStore trajectories() {
        return state.trajectories();
    }

    public InMemoryModelMetrics metrics() {
        return state.metrics();
    }

    public MemoryStore memories() {
        return state.memories();
    }

    public SkillApprovalFlow skillApprovals() {
        return state.skillApprovals();
    }

    public LearningGraphSnapshot learningGraph() {
        return state.learningGraph();
    }
}
