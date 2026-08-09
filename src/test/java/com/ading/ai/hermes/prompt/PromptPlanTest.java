package com.ading.ai.hermes.prompt;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptPlanTest {

    @Test
    void keepsStableContextAndVolatileSectionsInProviderCacheOrder() {
        PromptPlan plan = new PromptPlan(List.of(
                new PromptSection(PromptTier.STABLE, "identity", "You are Hermes."),
                new PromptSection(PromptTier.CONTEXT, "project", "Use Java 21."),
                new PromptSection(PromptTier.VOLATILE, "memory", "User prefers Chinese.")
        ));

        assertEquals(
                "## identity\nYou are Hermes.\n\n## project\nUse Java 21."
                        + "\n\n## memory\nUser prefers Chinese.",
                plan.systemPrompt()
        );
        assertEquals("## identity\nYou are Hermes.", plan.stablePrefix());
        assertEquals(plan.stablePrefix().length(), plan.cacheDescriptor().stablePrefixCharacters());
    }

    @Test
    void volatileChangesDoNotInvalidateTheStablePrefixFingerprint() {
        PromptPlan first = new PromptPlan(List.of(
                new PromptSection(PromptTier.STABLE, "identity", "You are Hermes."),
                new PromptSection(PromptTier.VOLATILE, "memory", "alpha")
        ));
        PromptPlan second = new PromptPlan(List.of(
                new PromptSection(PromptTier.STABLE, "identity", "You are Hermes."),
                new PromptSection(PromptTier.VOLATILE, "memory", "beta")
        ));
        PromptPlan changedStable = new PromptPlan(List.of(
                new PromptSection(PromptTier.STABLE, "identity", "You are Hermes Runtime."),
                new PromptSection(PromptTier.VOLATILE, "memory", "beta")
        ));

        assertEquals(
                first.cacheDescriptor().stablePrefixFingerprint(),
                second.cacheDescriptor().stablePrefixFingerprint()
        );
        assertNotEquals(
                first.cacheDescriptor().stablePrefixFingerprint(),
                changedStable.cacheDescriptor().stablePrefixFingerprint()
        );
    }

    @Test
    void rejectsSectionsThatWouldBreakCacheTierOrdering() {
        assertThrows(IllegalArgumentException.class, () -> new PromptPlan(List.of(
                new PromptSection(PromptTier.VOLATILE, "memory", "alpha"),
                new PromptSection(PromptTier.STABLE, "identity", "You are Hermes.")
        )));
    }
}
