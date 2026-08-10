package com.ading.ai.hermes.scheduler;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.control.AdmissionDecision;
import com.ading.ai.hermes.control.NewWorkPolicy;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CronScheduler {

    private final AgentRuntime runtime;
    private final CronDeliverySink deliverySink;
    private final IterationBudget defaultBudget;
    private final NewWorkPolicy newWorkPolicy;
    private final CronClaimStore claimStore;

    public CronScheduler(AgentRuntime runtime, CronDeliverySink deliverySink) {
        this(runtime, deliverySink, IterationBudget.maxTurns(4), NewWorkPolicy.allowAll());
    }

    public CronScheduler(AgentRuntime runtime, CronDeliverySink deliverySink, IterationBudget defaultBudget) {
        this(runtime, deliverySink, defaultBudget, NewWorkPolicy.allowAll());
    }

    public CronScheduler(
            AgentRuntime runtime,
            CronDeliverySink deliverySink,
            IterationBudget defaultBudget,
            NewWorkPolicy newWorkPolicy
    ) {
        this(runtime, deliverySink, defaultBudget, newWorkPolicy, new InMemoryCronClaimStore());
    }

    public CronScheduler(
            AgentRuntime runtime,
            CronDeliverySink deliverySink,
            IterationBudget defaultBudget,
            NewWorkPolicy newWorkPolicy,
            CronClaimStore claimStore
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.deliverySink = Objects.requireNonNull(deliverySink, "deliverySink must not be null");
        this.defaultBudget = Objects.requireNonNull(defaultBudget, "defaultBudget must not be null");
        this.newWorkPolicy = Objects.requireNonNull(newWorkPolicy, "newWorkPolicy must not be null");
        this.claimStore = Objects.requireNonNull(claimStore, "claimStore must not be null");
    }

    public CronTickResult tick(List<CronJob> jobs, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        AdmissionDecision admission = Objects.requireNonNull(
                newWorkPolicy.evaluate(),
                "newWorkPolicy result must not be null"
        );
        if (!admission.allowed()) {
            return new CronTickResult(List.of(), admission.reason());
        }
        List<CronRunRecord> runs = new ArrayList<>();
        List<CronDeliveryResult> deliveries = new ArrayList<>();
        List<CronRunFailure> failures = new ArrayList<>();
        for (CronJob job : jobs == null ? List.<CronJob>of() : jobs) {
            if (!job.dueAt(now)) {
                continue;
            }
            String fireKey = job.fireKey();
            if (!claimStore.tryClaim(fireKey)) {
                continue;
            }
            try {
                CronRunRecord runRecord = run(job, now, fireKey);
                runs.add(runRecord);
                deliveries.add(deliver(runRecord));
            } catch (RuntimeException error) {
                claimStore.release(fireKey);
                failures.add(new CronRunFailure(
                        fireKey,
                        job.id(),
                        CronFailureStage.RUNTIME,
                        errorMessage(error)
                ));
            }
        }
        return new CronTickResult(runs, deliveries, failures, "");
    }

    public CronDeliveryResult deliver(CronRunRecord runRecord) {
        Objects.requireNonNull(runRecord, "runRecord must not be null");
        try {
            deliverySink.deliver(runRecord);
            return CronDeliveryResult.delivered(runRecord);
        } catch (RuntimeException error) {
            return CronDeliveryResult.failed(runRecord, error);
        }
    }

    private CronRunRecord run(CronJob job, Instant now, String fireKey) {
        AgentRunResult result = runtime.run(AgentRunRequest.from(
                "cron",
                "cron-" + UUID.nameUUIDFromBytes(fireKey.getBytes(StandardCharsets.UTF_8)),
                job.prompt(),
                defaultBudget,
                Map.of(
                        CronMetadata.JOB_ID, job.id(),
                        CronMetadata.FIRE_KEY, fireKey,
                        CronMetadata.DELIVERY_KIND, job.deliveryTarget().kind(),
                        CronMetadata.DELIVERY_DESTINATION, job.deliveryTarget().destination()
                )
        ));
        return new CronRunRecord(
                "cron-" + fireKey,
                job.id(),
                job.name(),
                fireKey,
                now,
                job.schedule().nextAfter(job.nextRunAt()),
                job.deliveryTarget(),
                result.finalAnswer(),
                result.finishReason()
        );
    }

    private static String errorMessage(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
