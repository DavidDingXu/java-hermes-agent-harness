package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.observability.TrajectoryRecord;
import com.ading.ai.hermes.skill.SelfImprovementResult;
import java.util.Objects;

public record RuntimeRunArtifacts(
        TrajectoryRecord trajectory,
        SelfImprovementResult selfImprovement
) {
    public RuntimeRunArtifacts {
        Objects.requireNonNull(trajectory, "trajectory must not be null");
        Objects.requireNonNull(selfImprovement, "selfImprovement must not be null");
    }
}
