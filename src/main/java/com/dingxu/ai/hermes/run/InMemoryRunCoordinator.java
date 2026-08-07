package com.dingxu.ai.hermes.run;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRunCoordinator implements RunCoordinator {

    private final Map<String, MutableRun> runs = new ConcurrentHashMap<>();
    private final Map<String, String> activeBySession = new ConcurrentHashMap<>();

    @Override
    public RunSnapshot start(String sessionId, String input) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        String runId = UUID.randomUUID().toString();
        MutableRun run = new MutableRun(runId, sessionId, input);
        synchronized (run) {
            run.addEvent(RunEventType.RUN_STARTED, input);
        }
        runs.put(runId, run);
        String previous = activeBySession.putIfAbsent(sessionId, runId);
        if (previous != null) {
            runs.remove(runId, run);
            throw new IllegalStateException("session already has an active run: " + sessionId);
        }
        synchronized (run) {
            return run.snapshot();
        }
    }

    @Override
    public RunSnapshot snapshot(String runId) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            return run.snapshot();
        }
    }

    @Override
    public Optional<RunSnapshot> activeRunForSession(String sessionId) {
        String runId = activeBySession.get(sessionId);
        return runId == null ? Optional.empty() : Optional.of(snapshot(runId));
    }

    @Override
    public void emit(String runId, RunEventType type, String payload) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            requireNonTerminal(run);
            run.addEvent(type, payload);
        }
    }

    @Override
    public List<RunEvent> eventsAfter(String runId, long afterIndex) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            return run.events.stream().filter(event -> event.index() > afterIndex).toList();
        }
    }

    @Override
    public void waitForApproval(String runId, RunApproval approval) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            if (run.status != RunStatus.RUNNING) {
                throw new IllegalStateException("run is not ready to request approval");
            }
            run.pendingApproval = approval;
            run.status = RunStatus.WAITING_APPROVAL;
            run.addEvent(RunEventType.APPROVAL_REQUIRED, approval.summary());
        }
    }

    @Override
    public void resolveApproval(String runId, String approvalId, boolean approved) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            if (run.status != RunStatus.WAITING_APPROVAL || run.pendingApproval == null) {
                throw new IllegalStateException("run has no pending approval");
            }
            if (!run.pendingApproval.approvalId().equals(approvalId)) {
                throw new IllegalArgumentException("approval id does not match pending approval");
            }
            run.pendingApproval = null;
            if (approved) {
                run.status = RunStatus.RUNNING;
                run.addEvent(RunEventType.APPROVAL_RESOLVED, "approved");
            } else {
                run.status = RunStatus.STOPPED;
                run.addEvent(RunEventType.APPROVAL_RESOLVED, "denied");
                activeBySession.remove(run.sessionId, run.runId);
            }
        }
    }

    @Override
    public void submitBusyInput(String runId, String input, BusyInputMode mode) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            requireNonTerminal(run);
            switch (mode) {
                case QUEUE -> {
                    run.queuedInputs.addLast(input);
                    run.addEvent(RunEventType.INPUT_QUEUED, input);
                }
                case STEER -> {
                    run.pendingSteer = run.pendingSteer == null
                            ? input
                            : run.pendingSteer + "\n" + input;
                    run.addEvent(RunEventType.INPUT_STEERED, input);
                }
                case INTERRUPT -> {
                    run.status = RunStatus.STOP_REQUESTED;
                    run.addEvent(RunEventType.STOP_REQUESTED, input);
                }
            }
        }
    }

    @Override
    public Optional<String> takePendingSteer(String runId) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            String pending = run.pendingSteer;
            run.pendingSteer = null;
            return Optional.ofNullable(pending);
        }
    }

    @Override
    public Optional<String> takeQueuedInput(String runId) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            return Optional.ofNullable(run.queuedInputs.pollFirst());
        }
    }

    @Override
    public void complete(String runId, String output) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            requireStatus(run, RunStatus.RUNNING, "run is not ready to complete");
            run.output = output;
            run.status = RunStatus.COMPLETED;
            run.addEvent(RunEventType.COMPLETED, output);
            activeBySession.remove(run.sessionId, run.runId);
        }
    }

    @Override
    public void stop(String runId, String reason) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            if (run.status != RunStatus.STOP_REQUESTED && run.status != RunStatus.RUNNING) {
                throw new IllegalStateException("run is not ready to stop: " + run.status);
            }
            run.output = reason == null ? "" : reason;
            run.status = RunStatus.STOPPED;
            run.addEvent(RunEventType.STOPPED, run.output);
            activeBySession.remove(run.sessionId, run.runId);
        }
    }

    @Override
    public void fail(String runId, String error) {
        MutableRun run = requireRun(runId);
        synchronized (run) {
            requireNonTerminal(run);
            run.output = error;
            run.status = RunStatus.FAILED;
            run.addEvent(RunEventType.FAILED, error);
            activeBySession.remove(run.sessionId, run.runId);
        }
    }

    private MutableRun requireRun(String runId) {
        MutableRun run = runs.get(runId);
        if (run == null) {
            throw new IllegalArgumentException("run does not exist: " + runId);
        }
        return run;
    }

    private static void requireNonTerminal(MutableRun run) {
        if (run.status == RunStatus.COMPLETED
                || run.status == RunStatus.FAILED
                || run.status == RunStatus.STOPPED) {
            throw new IllegalStateException("run is already terminal: " + run.status);
        }
    }

    private static void requireStatus(MutableRun run, RunStatus expected, String message) {
        if (run.status != expected) {
            throw new IllegalStateException(message + ": " + run.status);
        }
    }

    private static final class MutableRun {
        private final String runId;
        private final String sessionId;
        private final String input;
        private final List<RunEvent> events = new ArrayList<>();
        private final ArrayDeque<String> queuedInputs = new ArrayDeque<>();
        private RunStatus status = RunStatus.RUNNING;
        private String output = "";
        private String pendingSteer;
        private RunApproval pendingApproval;

        private MutableRun(String runId, String sessionId, String input) {
            this.runId = runId;
            this.sessionId = sessionId;
            this.input = input;
        }

        private void addEvent(RunEventType type, String payload) {
            events.add(new RunEvent(events.size() + 1L, type, payload, Instant.now()));
        }

        private RunSnapshot snapshot() {
            return new RunSnapshot(
                    runId,
                    sessionId,
                    status,
                    input,
                    output,
                    status == RunStatus.STOP_REQUESTED,
                    queuedInputs.size(),
                    Optional.ofNullable(pendingSteer),
                    Optional.ofNullable(pendingApproval),
                    events.size()
            );
        }
    }
}
