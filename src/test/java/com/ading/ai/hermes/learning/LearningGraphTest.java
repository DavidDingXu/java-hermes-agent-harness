package com.ading.ai.hermes.learning;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningGraphTest {

    @Test
    void connectsExplicitSkillsAndRelatedMemoryWithoutMutatingInputs() {
        LearningMemory memory = new LearningMemory("memory:java-tests", "Project uses Maven for Java tests.");
        LearningSkill testing = new LearningSkill("skill:java-testing", "Java testing", "Run focused Maven tests", List.of());
        LearningSkill debugging = new LearningSkill(
                "skill:debugging",
                "Debugging",
                "Inspect failures before changing Java code",
                List.of(testing.id())
        );

        LearningGraphSnapshot graph = LearningGraph.build(List.of(memory), List.of(testing, debugging));

        assertEquals(3, graph.nodes().size());
        assertTrue(graph.edges().contains(new LearningEdge(debugging.id(), testing.id(), LearningEdgeKind.RELATED_SKILL)));
        assertTrue(graph.edges().stream().anyMatch(edge ->
                edge.fromId().equals(memory.id())
                        && edge.toId().equals(testing.id())
                        && edge.kind() == LearningEdgeKind.MEMORY_SKILL
        ));
        assertEquals(2, graph.edgeCount());
    }
}
