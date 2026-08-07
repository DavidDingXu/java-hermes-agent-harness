package com.dingxu.ai.hermes.skill;

import java.util.EnumSet;
import java.util.Set;

public final class TrustedSkillPolicy {

    private final Set<SkillSourceKind> autoLoadSources;

    private TrustedSkillPolicy(Set<SkillSourceKind> autoLoadSources) {
        this.autoLoadSources = Set.copyOf(autoLoadSources);
    }

    public static TrustedSkillPolicy defaultPolicy() {
        return new TrustedSkillPolicy(EnumSet.of(SkillSourceKind.LOCAL, SkillSourceKind.OFFICIAL));
    }

    public SkillTrustDecision evaluate(SkillManifest skill) {
        if (!skill.enabled()) {
            return SkillTrustDecision.block("skill_disabled");
        }
        SkillSourceKind sourceKind = skill.provenance().sourceKind();
        if (autoLoadSources.contains(sourceKind)) {
            return SkillTrustDecision.allow("trusted_source");
        }
        if (sourceKind == SkillSourceKind.AGENT_CREATED) {
            return SkillTrustDecision.stage("agent_created_requires_review");
        }
        return SkillTrustDecision.stage("untrusted_source");
    }
}
