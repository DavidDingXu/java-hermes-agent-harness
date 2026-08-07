package com.ading.ai.hermes.scheduler;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.IterationBudget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CronScheduler {

    private final AgentRuntime runtime;
    private final CronDeliverySink deliverySink;
    private final IterationBudget defaultBudget;
    private final Set<String> claimedFireKeys = new HashSet<>();

    public CronScheduler(AgentRuntime runtime, CronDeliverySink deliverySink) {
        this(runtime, deliverySink, IterationBudget.maxTurns(4));
    }

    public CronScheduler(AgentRuntime runtime, CronDeliverySink deliverySink, IterationBudget defaultBudget) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.deliverySink = Objects.requireNonNull(deliverySink, "deliverySink must not be null");
        this.defaultBudget = Objects.requireNonNull(defaultBudget, "defaultBudget must not be null");
    }

    public CronTickResult tick(List<CronJob> jobs, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        List<CronRunRecord> runs = new ArrayList<>();
        for (CronJob job : jobs == null ? List.<CronJob>of() : jobs) {
            if (!job.dueAt(now)) {
                continue;
            }
            String fireKey = job.fireKey(now);
            if (!claimedFireKeys.add(fireKey)) {
                continue;
            }
            CronRunRecord runRecord = run(job, now, fireKey);
            deliverySink.deliver(runRecord);
            runs.add(runRecord);
        }
        return new CronTickResult(runs);
    }

    private CronRunRecord run(CronJob job, Instant now, String fireKey) {
        AgentRunResult result = runtime.run(AgentRunRequest.start(job.prompt(), defaultBudget));
        return new CronRunRecord(
                "cron-" + fireKey,
                job.id(),
                job.name(),
                fireKey,
                now,
                job.schedule().nextAfter(now),
                job.deliveryTarget(),
                result.finalAnswer(),
                result.finishReason()
        );
    }
}
