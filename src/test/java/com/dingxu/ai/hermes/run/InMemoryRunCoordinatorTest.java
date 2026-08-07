package com.dingxu.ai.hermes.run;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRunCoordinatorTest {

    @Test
    void exposesIncrementalEventsAcrossTheRunLifecycle() {
        InMemoryRunCoordinator coordinator = new InMemoryRunCoordinator();
        RunSnapshot started = coordinator.start("session-1", "fix the test");
        coordinator.emit(started.runId(), RunEventType.MODEL_STARTED, "turn=1");
        coordinator.emit(started.runId(), RunEventType.TOOL_FINISHED, "read_file:ok");
        coordinator.complete(started.runId(), "done");

        List<RunEvent> tail = coordinator.eventsAfter(started.runId(), 1);
        RunSnapshot completed = coordinator.snapshot(started.runId());

        assertEquals(List.of(RunEventType.MODEL_STARTED, RunEventType.TOOL_FINISHED, RunEventType.COMPLETED),
                tail.stream().map(RunEvent::type).toList());
        assertEquals(RunStatus.COMPLETED, completed.status());
        assertEquals("done", completed.output());
        assertFalse(coordinator.activeRunForSession("session-1").isPresent());
    }

    @Test
    void resumesTheSameRunAfterApproval() {
        InMemoryRunCoordinator coordinator = new InMemoryRunCoordinator();
        RunSnapshot run = coordinator.start("session-1", "delete generated file");

        coordinator.waitForApproval(run.runId(), new RunApproval("approval-1", "delete_file", "target/tmp.txt"));
        assertEquals(RunStatus.WAITING_APPROVAL, coordinator.snapshot(run.runId()).status());

        coordinator.resolveApproval(run.runId(), "approval-1", true);

        assertEquals(RunStatus.RUNNING, coordinator.snapshot(run.runId()).status());
        assertTrue(coordinator.eventsAfter(run.runId(), 0).stream()
                .anyMatch(event -> event.type() == RunEventType.APPROVAL_RESOLVED));
    }

    @Test
    void keepsQueueSteerAndInterruptSemanticsDistinct() {
        InMemoryRunCoordinator coordinator = new InMemoryRunCoordinator();
        RunSnapshot run = coordinator.start("session-1", "initial");

        coordinator.submitBusyInput(run.runId(), "next task", BusyInputMode.QUEUE);
        coordinator.submitBusyInput(run.runId(), "change the current approach", BusyInputMode.STEER);

        assertEquals("change the current approach", coordinator.takePendingSteer(run.runId()).orElseThrow());
        assertEquals("next task", coordinator.takeQueuedInput(run.runId()).orElseThrow());
        assertEquals(RunStatus.RUNNING, coordinator.snapshot(run.runId()).status());

        coordinator.submitBusyInput(run.runId(), "stop now", BusyInputMode.INTERRUPT);

        assertEquals(RunStatus.STOP_REQUESTED, coordinator.snapshot(run.runId()).status());
        assertTrue(coordinator.snapshot(run.runId()).stopRequested());

        coordinator.stop(run.runId(), "stop now");
        assertEquals(RunStatus.STOPPED, coordinator.snapshot(run.runId()).status());
        assertFalse(coordinator.activeRunForSession("session-1").isPresent());
    }

    @Test
    void cannotCompleteWhileApprovalIsPendingOrStopWasRequested() {
        InMemoryRunCoordinator coordinator = new InMemoryRunCoordinator();
        RunSnapshot approvalRun = coordinator.start("approval-session", "delete file");
        coordinator.waitForApproval(
                approvalRun.runId(),
                new RunApproval("approval-1", "delete_file", "target/tmp.txt")
        );

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.complete(approvalRun.runId(), "done")
        );
        assertEquals(RunStatus.WAITING_APPROVAL, coordinator.snapshot(approvalRun.runId()).status());

        RunSnapshot stopRun = coordinator.start("stop-session", "long task");
        coordinator.submitBusyInput(stopRun.runId(), "stop now", BusyInputMode.INTERRUPT);

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.complete(stopRun.runId(), "done")
        );
        assertEquals(RunStatus.STOP_REQUESTED, coordinator.snapshot(stopRun.runId()).status());
    }
}
