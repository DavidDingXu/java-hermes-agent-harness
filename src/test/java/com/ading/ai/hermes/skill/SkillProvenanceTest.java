package com.ading.ai.hermes.skill;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SkillProvenanceTest {

    @Test
    void computesStableContentHashFromSkillIdentityAndInstructions() {
        SkillManifest skill = skillWithSource(SkillSourceKind.OFFICIAL, "official/java-testing", true);

        SkillProvenance provenance = skill.provenance();

        assertEquals(SkillSourceKind.OFFICIAL, provenance.sourceKind());
        assertEquals("official/java-testing", provenance.sourceId());
        assertEquals(64, provenance.contentHash().length());
    }

    @Test
    void contentHashChangesWhenInstructionsChange() {
        SkillManifest first = skillWithInstructions("Run Maven tests.");
        SkillManifest second = skillWithInstructions("Run focused Maven tests first.");

        assertNotEquals(first.provenance().contentHash(), second.provenance().contentHash());
    }

    @Test
    void defaultProvenanceUsesNormalizedInstructions() {
        SkillManifest withMarkdownTitle = new SkillManifest(
                "java-testing",
                "Run focused Java tests",
                "1.0.0",
                true,
                List.of("maven"),
                """
                        # Java Testing

                        Run Maven tests.
                        """
        );
        SkillManifest normalized = skillWithSource(SkillSourceKind.LOCAL, "local/java-testing", true);

        assertEquals("Run Maven tests.", withMarkdownTitle.instructions());
        assertEquals(normalized.provenance().contentHash(), withMarkdownTitle.provenance().contentHash());
    }

    @Test
    void trustedPolicyAllowsLocalAndOfficialSkillsToAutoLoad() {
        TrustedSkillPolicy policy = TrustedSkillPolicy.defaultPolicy();

        assertEquals(SkillTrustDecision.allow("trusted_source"),
                policy.evaluate(skillWithSource(SkillSourceKind.LOCAL, "local/java-testing", true)));
        assertEquals(SkillTrustDecision.allow("trusted_source"),
                policy.evaluate(skillWithSource(SkillSourceKind.OFFICIAL, "official/java-testing", true)));
    }

    @Test
    void trustedPolicyStagesCommunityAndAgentCreatedSkills() {
        TrustedSkillPolicy policy = TrustedSkillPolicy.defaultPolicy();

        assertEquals(SkillTrustDecision.stage("untrusted_source"),
                policy.evaluate(skillWithSource(SkillSourceKind.COMMUNITY, "skills-sh/java-testing", true)));
        assertEquals(SkillTrustDecision.stage("agent_created_requires_review"),
                policy.evaluate(skillWithSource(SkillSourceKind.AGENT_CREATED, "agent/java-testing", true)));
    }

    @Test
    void disabledSkillIsNeverAllowedToAutoLoad() {
        TrustedSkillPolicy policy = TrustedSkillPolicy.defaultPolicy();

        SkillTrustDecision decision = policy.evaluate(skillWithSource(SkillSourceKind.OFFICIAL, "official/java-testing", false));

        assertEquals(SkillTrustDecision.block("skill_disabled"), decision);
    }

    private SkillManifest skillWithSource(SkillSourceKind sourceKind, String sourceId, boolean enabled) {
        return new SkillManifest(
                "java-testing",
                "Run focused Java tests",
                "1.0.0",
                enabled,
                List.of("maven"),
                "Run Maven tests.",
                SkillProvenance.fromContent(sourceKind, sourceId, "java-testing", "1.0.0", "Run Maven tests.")
        );
    }

    private SkillManifest skillWithInstructions(String instructions) {
        return new SkillManifest(
                "java-testing",
                "Run focused Java tests",
                "1.0.0",
                true,
                List.of("maven"),
                instructions,
                SkillProvenance.fromContent(SkillSourceKind.OFFICIAL, "official/java-testing", "java-testing", "1.0.0", instructions)
        );
    }
}
