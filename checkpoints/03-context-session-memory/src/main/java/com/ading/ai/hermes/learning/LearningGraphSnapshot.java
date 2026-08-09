package com.ading.ai.hermes.learning;

import java.util.List;

public record LearningGraphSnapshot(List<LearningNode> nodes, List<LearningEdge> edges, double density) {

    public LearningGraphSnapshot {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        if (density < 0.0 || density > 1.0) {
            throw new IllegalArgumentException("density must be between 0 and 1");
        }
    }

    public int edgeCount() {
        return edges.size();
    }
}
