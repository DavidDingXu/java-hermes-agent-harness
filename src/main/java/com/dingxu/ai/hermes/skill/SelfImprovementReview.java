package com.dingxu.ai.hermes.skill;

import com.dingxu.ai.hermes.memory.MemoryCandidate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SelfImprovementReview(
        String sessionId,
        boolean recoveredFailure,
        List<MemoryCandidate> memoryCandidates,
        Optional<SkillCandidate> pendingSkillCandidate
) {

    public SelfImprovementReview {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(memoryCandidates, "memoryCandidates must not be null");
        Objects.requireNonNull(pendingSkillCandidate, "pendingSkillCandidate must not be null");
        sessionId = sessionId.trim();
        memoryCandidates = List.copyOf(memoryCandidates);
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }
}
