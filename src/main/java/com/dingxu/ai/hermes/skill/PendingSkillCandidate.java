package com.dingxu.ai.hermes.skill;

import java.util.Objects;

public record PendingSkillCandidate(String id, SkillCandidate candidate) {

    public PendingSkillCandidate {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        id = id.trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("pending id must not be blank");
        }
    }
}
