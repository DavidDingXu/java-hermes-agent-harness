package com.dingxu.ai.hermes.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MemoryStore {

    private final MemoryPolicy policy;
    private final int memoryLimit;
    private final int userLimit;
    private final List<String> memoryEntries = new ArrayList<>();
    private final List<String> userEntries = new ArrayList<>();

    public MemoryStore(MemoryPolicy policy, int memoryLimit, int userLimit) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        if (memoryLimit <= 0) {
            throw new IllegalArgumentException("memoryLimit must be positive");
        }
        if (userLimit <= 0) {
            throw new IllegalArgumentException("userLimit must be positive");
        }
        this.memoryLimit = memoryLimit;
        this.userLimit = userLimit;
    }

    public MemoryWriteResult consider(MemoryCandidate candidate) {
        MemoryDecision decision = policy.evaluate(candidate);
        if (decision.kind() == MemoryDecisionKind.REJECT) {
            return new MemoryWriteResult(false, decision);
        }

        List<String> targetEntries = mutableEntries(decision.target());
        if (targetEntries.contains(decision.normalizedContent())) {
            return new MemoryWriteResult(false, MemoryDecision.reject("duplicate"));
        }
        if (wouldExceedLimit(targetEntries, decision)) {
            return new MemoryWriteResult(false, MemoryDecision.reject(limitReason(decision.target())));
        }

        targetEntries.add(decision.normalizedContent());
        return new MemoryWriteResult(true, decision);
    }

    public List<String> entries(MemoryTarget target) {
        return List.copyOf(mutableEntries(target));
    }

    private List<String> mutableEntries(MemoryTarget target) {
        Objects.requireNonNull(target, "target must not be null");
        return switch (target) {
            case MEMORY -> memoryEntries;
            case USER -> userEntries;
        };
    }

    private boolean wouldExceedLimit(List<String> targetEntries, MemoryDecision decision) {
        int currentCharacters = 0;
        for (String entry : targetEntries) {
            currentCharacters += entry.length();
        }
        int separatorCharacters = targetEntries.isEmpty() ? 0 : 1;
        return currentCharacters + separatorCharacters + decision.normalizedContent().length() > limit(decision.target());
    }

    private int limit(MemoryTarget target) {
        return switch (target) {
            case MEMORY -> memoryLimit;
            case USER -> userLimit;
        };
    }

    private String limitReason(MemoryTarget target) {
        return switch (target) {
            case MEMORY -> "memory_limit_exceeded";
            case USER -> "user_limit_exceeded";
        };
    }
}
