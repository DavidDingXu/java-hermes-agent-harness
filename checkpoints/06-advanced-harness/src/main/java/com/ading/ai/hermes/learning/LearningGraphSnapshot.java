package com.ading.ai.hermes.learning;

import java.util.List;

public record LearningGraphSnapshot(
        List<LearningNode> nodes,
        List<LearningEdge> edges,
        double density,
        List<LearningGraphDiagnostic> diagnostics
) {

    public LearningGraphSnapshot {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (density < 0.0 || density > 1.0) {
            throw new IllegalArgumentException("density must be between 0 and 1");
        }
    }

    public LearningGraphSnapshot(List<LearningNode> nodes, List<LearningEdge> edges, double density) {
        this(nodes, edges, density, List.of());
    }

    public int edgeCount() {
        return edges.size();
    }
}
