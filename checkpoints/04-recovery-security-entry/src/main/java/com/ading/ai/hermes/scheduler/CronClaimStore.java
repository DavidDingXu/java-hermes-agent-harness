package com.ading.ai.hermes.scheduler;

public interface CronClaimStore {

    boolean tryClaim(String fireKey);

    void release(String fireKey);
}
