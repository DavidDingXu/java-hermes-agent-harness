package com.dingxu.ai.hermes.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SkillApprovalFlow {

    private final List<PendingSkillCandidate> pending = new ArrayList<>();
    private final List<SkillManifest> approved = new ArrayList<>();
    private int nextId = 1;

    public PendingSkillCandidate submit(SkillCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        PendingSkillCandidate pendingCandidate = new PendingSkillCandidate("skill-candidate-" + nextId++, candidate);
        pending.add(pendingCandidate);
        return pendingCandidate;
    }

    public SkillManifest approve(String id) {
        PendingSkillCandidate candidate = removePending(id);
        SkillManifest skill = candidate.candidate().approveAsSkill("approved/" + candidate.id());
        approved.add(skill);
        return skill;
    }

    public void reject(String id) {
        removePending(id);
    }

    public List<PendingSkillCandidate> pending() {
        return List.copyOf(pending);
    }

    public List<SkillManifest> approvedSkills() {
        return List.copyOf(approved);
    }

    private PendingSkillCandidate removePending(String id) {
        Objects.requireNonNull(id, "id must not be null");
        for (int index = 0; index < pending.size(); index++) {
            PendingSkillCandidate candidate = pending.get(index);
            if (candidate.id().equals(id)) {
                pending.remove(index);
                return candidate;
            }
        }
        throw new IllegalArgumentException("pending skill candidate not found: " + id);
    }
}
