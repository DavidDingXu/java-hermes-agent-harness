package com.ading.ai.hermes.scheduler;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CronSchedulerTest {

    @Test
    void runsDueJobOnceAndDeliversFinalAnswer() {
        AtomicReference<AgentRunRequest> capturedRequest = new AtomicReference<>();
        AgentRuntime runtime = request -> {
            capturedRequest.set(request);
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "build is green",
                    AgentState.start(request.userMessage()).incrementTurns()
            );
        };
        RecordingDeliverySink deliverySink = new RecordingDeliverySink();
        CronScheduler scheduler = new CronScheduler(runtime, deliverySink);
        CronJob job = new CronJob(
                "job-1",
                "daily-build",
                "Check CI status",
                CronSchedule.everyMinutes(60),
                Instant.parse("2026-06-20T09:00:00Z"),
                DeliveryTarget.local("ci-report"),
                false
        );

        CronTickResult result = scheduler.tick(
                List.of(job),
                Instant.parse("2026-06-20T10:00:00Z")
        );

        assertEquals(1, result.runs().size());
        CronRunRecord record = result.runs().getFirst();
        assertEquals("job-1", record.jobId());
        assertEquals("job-1@2026-06-20T10:00:00Z", record.fireKey());
        assertEquals("build is green", record.finalAnswer());
        assertEquals(FinishReason.FINAL_ANSWER, record.finishReason());
        assertEquals(Instant.parse("2026-06-20T11:00:00Z"), record.nextRunAt());
        assertEquals("Check CI status", capturedRequest.get().userMessage());
        assertEquals(4, capturedRequest.get().budget().maxTurns());
        assertEquals(List.of(record), deliverySink.delivered());
    }

    @Test
    void skipsSameFireKeyAfterItWasClaimed() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        CronScheduler scheduler = new CronScheduler(request -> {
            runtimeCalls.incrementAndGet();
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "done",
                    AgentState.start(request.userMessage()).incrementTurns()
            );
        }, new RecordingDeliverySink());
        CronJob job = new CronJob(
                "job-2",
                "nightly",
                "Summarize logs",
                CronSchedule.everyMinutes(30),
                Instant.parse("2026-06-20T09:30:00Z"),
                DeliveryTarget.local("logs"),
                false
        );
        Instant tickTime = Instant.parse("2026-06-20T10:00:00Z");

        CronTickResult first = scheduler.tick(List.of(job), tickTime);
        CronTickResult second = scheduler.tick(List.of(job), tickTime);

        assertEquals(1, first.runs().size());
        assertTrue(second.runs().isEmpty());
        assertEquals(1, runtimeCalls.get());
    }

    @Test
    void skipsPausedAndFutureJobs() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        CronScheduler scheduler = new CronScheduler(request -> {
            runtimeCalls.incrementAndGet();
            return new AgentRunResult(FinishReason.FINAL_ANSWER, "unused", AgentState.start(request.userMessage()));
        }, new RecordingDeliverySink());
        Instant now = Instant.parse("2026-06-20T10:00:00Z");
        CronJob pausedJob = new CronJob(
                "job-paused",
                "paused",
                "Do not run",
                CronSchedule.everyMinutes(15),
                Instant.parse("2026-06-20T09:45:00Z"),
                DeliveryTarget.local("paused"),
                true
        );
        CronJob futureJob = new CronJob(
                "job-future",
                "future",
                "Run later",
                CronSchedule.everyMinutes(15),
                Instant.parse("2026-06-20T10:15:00Z"),
                DeliveryTarget.local("future"),
                false
        );

        CronTickResult result = scheduler.tick(List.of(pausedJob, futureJob), now);

        assertTrue(result.runs().isEmpty());
        assertEquals(0, runtimeCalls.get());
    }

    private static final class RecordingDeliverySink implements CronDeliverySink {
        private final java.util.ArrayList<CronRunRecord> delivered = new java.util.ArrayList<>();

        @Override
        public void deliver(CronRunRecord runRecord) {
            delivered.add(runRecord);
        }

        List<CronRunRecord> delivered() {
            return List.copyOf(delivered);
        }
    }
}
