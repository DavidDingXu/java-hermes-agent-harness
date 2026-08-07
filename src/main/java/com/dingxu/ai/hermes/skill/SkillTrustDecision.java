package com.dingxu.ai.hermes.skill;

import java.util.Objects;

public record SkillTrustDecision(SkillTrustAction action, String reason) {

    public SkillTrustDecision {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }

    public static SkillTrustDecision allow(String reason) {
        return new SkillTrustDecision(SkillTrustAction.ALLOW, reason);
    }

    public static SkillTrustDecision stage(String reason) {
        return new SkillTrustDecision(SkillTrustAction.STAGE, reason);
    }

    public static SkillTrustDecision block(String reason) {
        return new SkillTrustDecision(SkillTrustAction.BLOCK, reason);
    }
}
