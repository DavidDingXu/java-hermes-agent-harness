package com.ading.ai.hermes.learning;

import java.util.List;

public record LearningGraphDocument(
        List<LearningMemory> memories,
        List<LearningSkill> skills
) {

    public LearningGraphDocument {
        memories = memories == null ? List.of() : List.copyOf(memories);
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public LearningGraphSnapshot snapshot() {
        return LearningGraph.build(memories, skills);
    }
}
