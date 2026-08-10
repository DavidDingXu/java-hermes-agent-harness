package com.ading.ai.hermes.learning;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void reportsRelationshipsThatPointToUnknownSkills() {
        LearningSkill skill = new LearningSkill(
                "skill:debugging",
                "Debugging",
                "Inspect failures",
                List.of("skill:missing")
        );

        LearningGraphSnapshot graph = LearningGraph.build(List.of(), List.of(skill));

        assertEquals(1, graph.diagnostics().size());
        assertEquals(LearningDiagnosticCode.UNKNOWN_RELATED_SKILL, graph.diagnostics().getFirst().code());
        assertEquals("skill:missing", graph.diagnostics().getFirst().targetId());
    }

    @Test
    void rejectsADeleteThatWouldLeaveADanglingRelationship() {
        LearningSkill testing = new LearningSkill(
                "skill:java-testing", "Java testing", "Run focused tests", List.of()
        );
        LearningSkill debugging = new LearningSkill(
                "skill:debugging", "Debugging", "Inspect failures", List.of(testing.id())
        );
        LearningGraphDocument original = new LearningGraphDocument(List.of(), List.of(testing, debugging));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> LearningGraphMutations.deleteSkill(original, testing.id())
        );

        assertTrue(error.getMessage().contains("skill:java-testing"));
        assertEquals(List.of(testing, debugging), original.skills());
    }

    @Test
    void appliesAValidMutationAsANewValidatedDocument() {
        LearningGraphDocument original = new LearningGraphDocument(List.of(), List.of());
        LearningSkill testing = new LearningSkill(
                "skill:java-testing", "Java testing", "Run focused tests", List.of()
        );

        LearningGraphDocument updated = LearningGraphMutations.upsertSkill(original, testing);

        assertTrue(original.skills().isEmpty());
        assertEquals(List.of(testing), updated.skills());
        assertTrue(updated.snapshot().diagnostics().isEmpty());
    }
}
