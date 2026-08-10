package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.learning.LearningGraphDocument;
import com.ading.ai.hermes.learning.LearningGraphMutations;
import com.ading.ai.hermes.learning.LearningSkill;
import com.ading.ai.hermes.prompt.PromptPlan;
import com.ading.ai.hermes.prompt.PromptSection;
import com.ading.ai.hermes.prompt.PromptTier;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckpointContractTest {

    @Test
    void keepsTheStablePromptFingerprintIndependentFromVolatileFacts() {
        PromptPlan first = planWithRunNote("first run");
        PromptPlan second = planWithRunNote("second run");

        assertEquals(
                first.cacheDescriptor().stablePrefixFingerprint(),
                second.cacheDescriptor().stablePrefixFingerprint()
        );
        assertThrows(IllegalArgumentException.class, () -> new PromptPlan(List.of(
                new PromptSection(PromptTier.VOLATILE, "run", "now"),
                new PromptSection(PromptTier.STABLE, "policy", "always")
        )));
    }

    @Test
    void rejectsAMutationThatWouldLeaveADanglingSkillRelation() {
        LearningSkill testing = new LearningSkill(
                "skill-testing", "Java testing", "Run focused tests", List.of()
        );
        LearningSkill debugging = new LearningSkill(
                "skill-debugging", "Debugging", "Inspect failures", List.of(testing.id())
        );
        LearningGraphDocument original = new LearningGraphDocument(
                List.of(), List.of(testing, debugging)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> LearningGraphMutations.deleteSkill(original, testing.id())
        );
        assertEquals(List.of(testing, debugging), original.skills());
    }

    private static PromptPlan planWithRunNote(String note) {
        return new PromptPlan(List.of(
                new PromptSection(PromptTier.STABLE, "policy", "always inspect evidence"),
                new PromptSection(PromptTier.VOLATILE, "run-note", note)
        ));
    }
}
