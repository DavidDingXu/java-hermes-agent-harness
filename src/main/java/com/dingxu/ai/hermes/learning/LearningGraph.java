package com.dingxu.ai.hermes.learning;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LearningGraph {

    private LearningGraph() {
    }

    public static LearningGraphSnapshot build(List<LearningMemory> memories, List<LearningSkill> skills) {
        List<LearningMemory> memorySnapshot = List.copyOf(memories);
        List<LearningSkill> skillSnapshot = List.copyOf(skills);
        validateUniqueIds(memorySnapshot, skillSnapshot);

        List<LearningNode> nodes = new ArrayList<>();
        memorySnapshot.forEach(memory -> nodes.add(new LearningNode(
                memory.id(), LearningNodeKind.MEMORY, memory.id(), memory.content()
        )));
        skillSnapshot.forEach(skill -> nodes.add(new LearningNode(
                skill.id(), LearningNodeKind.SKILL, skill.name(), skill.description()
        )));

        Set<String> skillIds = new HashSet<>();
        skillSnapshot.forEach(skill -> skillIds.add(skill.id()));
        Set<LearningEdge> edges = new LinkedHashSet<>();
        for (LearningSkill skill : skillSnapshot) {
            for (String relatedId : skill.relatedSkillIds()) {
                if (skillIds.contains(relatedId) && !skill.id().equals(relatedId)) {
                    edges.add(new LearningEdge(skill.id(), relatedId, LearningEdgeKind.RELATED_SKILL));
                }
            }
        }
        for (LearningMemory memory : memorySnapshot) {
            Set<String> memoryTerms = terms(memory.content());
            for (LearningSkill skill : skillSnapshot) {
                Set<String> shared = new HashSet<>(memoryTerms);
                shared.retainAll(terms(skill.name() + " " + skill.description()));
                if (shared.size() >= 2) {
                    edges.add(new LearningEdge(memory.id(), skill.id(), LearningEdgeKind.MEMORY_SKILL));
                }
            }
        }

        int possibleEdges = nodes.size() < 2 ? 0 : nodes.size() * (nodes.size() - 1);
        double density = possibleEdges == 0 ? 0.0 : (double) edges.size() / possibleEdges;
        return new LearningGraphSnapshot(nodes, List.copyOf(edges), density);
    }

    private static void validateUniqueIds(List<LearningMemory> memories, List<LearningSkill> skills) {
        Set<String> ids = new HashSet<>();
        for (LearningMemory memory : memories) {
            if (!ids.add(memory.id())) {
                throw new IllegalArgumentException("duplicate learning node id: " + memory.id());
            }
        }
        for (LearningSkill skill : skills) {
            if (!ids.add(skill.id())) {
                throw new IllegalArgumentException("duplicate learning node id: " + skill.id());
            }
        }
    }

    private static Set<String> terms(String text) {
        Set<String> terms = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 3) {
                terms.add(token);
            }
        }
        return terms;
    }
}
