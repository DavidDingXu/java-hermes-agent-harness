package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.delegate.DelegationPolicy;
import com.ading.ai.hermes.delegate.DelegationRequest;
import com.ading.ai.hermes.delegate.SubAgentRunner;
import com.ading.ai.hermes.delegate.SubAgentTask;
import com.ading.ai.hermes.prompt.SystemReminderPolicy;
import com.ading.ai.hermes.scheduler.CronJob;
import com.ading.ai.hermes.scheduler.CronSchedule;
import com.ading.ai.hermes.scheduler.CronScheduler;
import com.ading.ai.hermes.scheduler.DeliveryTarget;
import com.ading.ai.hermes.verification.CompletionEvidence;
import com.ading.ai.hermes.verification.CompletionGate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointContractTest {

    @Test
    void rejectsACompletionWithoutEvidenceAndPreservesTheReason() {
        AgentRunResult result = new AgentRunResult(
                FinishReason.FINAL_ANSWER,
                "done",
                AgentState.start("change code").incrementTurns()
        );
        var decision = new CompletionGate(ignored -> CompletionEvidence.reject(
                "verification failed"
        )).evaluate(result);
        AgentEvent event = AgentEvent.completionRejected(decision.detail());

        assertTrue(decision.eligible());
        assertTrue(!decision.accepted());
        assertEquals(AgentEventKind.COMPLETION_REJECTED, event.kind());
        assertEquals("verification failed", event.text());
    }

    @Test
    void derivesAReminderFromStructuredToolFailureState() {
        AgentState state = AgentState.start("read config")
                .append(AgentEvent.toolRequested(new ToolRequest(
                        "call-1", "read_file", Map.of("path", "config/application.yml")
                )))
                .append(AgentEvent.toolObserved(ToolObservation.executionFailure(
                        "call-1", "temporarily unavailable"
                )));

        var reminders = SystemReminderPolicy.standard().remindersFor(state);

        assertEquals(List.of("tool-failure"), reminders.stream().map(item -> item.code()).toList());
    }

    @Test
    void releasesAFailedCronFireForRetryWithoutChangingItsIdentity() {
        AtomicInteger calls = new AtomicInteger();
        CronScheduler scheduler = new CronScheduler(request -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("provider unavailable");
            }
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "recovered",
                    AgentState.start(request.userMessage()).incrementTurns()
            );
        }, run -> { });
        CronJob job = new CronJob(
                "job-1", "daily", "inspect", CronSchedule.everyMinutes(30),
                Instant.parse("2026-08-10T10:00:00Z"), DeliveryTarget.local("console"), false
        );

        var failed = scheduler.tick(List.of(job), Instant.parse("2026-08-10T10:01:00Z"));
        var retried = scheduler.tick(List.of(job), Instant.parse("2026-08-10T10:02:00Z"));

        assertEquals("job-1@2026-08-10T10:00:00Z", failed.failures().getFirst().fireKey());
        assertEquals(1, retried.runs().size());
    }

    @Test
    void givesADelegatedTaskItsOwnConversation() {
        AtomicReference<String> conversation = new AtomicReference<>();
        SubAgentRunner runner = new SubAgentRunner(request -> {
            conversation.set(request.conversationId());
            return new AgentRunResult(
                    FinishReason.FINAL_ANSWER,
                    "done",
                    AgentState.start(request.userMessage()).incrementTurns()
            );
        }, new DelegationPolicy(1, com.ading.ai.hermes.core.IterationBudget.maxTurns(3)));

        var result = runner.run(new DelegationRequest(
                "delegation-1", "parent-run", "parent-session",
                List.of(new SubAgentTask(
                        "read-only", "inspect", "", List.of("workspace-read"), null
                ))
        ));

        assertEquals("parent-session.subagent.delegation-1.read-only", conversation.get());
        assertTrue(result.results().getFirst().conversationId().contains("subagent"));
    }
}
