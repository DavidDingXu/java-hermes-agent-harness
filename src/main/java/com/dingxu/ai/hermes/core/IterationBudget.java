package com.dingxu.ai.hermes.core;

public record IterationBudget(int maxTurns) {

    public IterationBudget {
        if (maxTurns < 1) {
            throw new IllegalArgumentException("maxTurns must be at least 1");
        }
    }

    public static IterationBudget maxTurns(int maxTurns) {
        return new IterationBudget(maxTurns);
    }

    public boolean allows(int turnsUsed) {
        return turnsUsed < maxTurns;
    }
}
