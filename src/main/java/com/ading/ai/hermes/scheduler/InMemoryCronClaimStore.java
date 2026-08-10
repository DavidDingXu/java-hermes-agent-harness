package com.ading.ai.hermes.scheduler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCronClaimStore implements CronClaimStore {

    private final Set<String> claimedFireKeys = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryClaim(String fireKey) {
        return claimedFireKeys.add(fireKey);
    }

    @Override
    public void release(String fireKey) {
        claimedFireKeys.remove(fireKey);
    }
}
