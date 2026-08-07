package com.ading.ai.hermes.run;

import java.util.List;
import java.util.Optional;

public interface RunCoordinator {

    RunSnapshot start(String sessionId, String input);

    RunSnapshot snapshot(String runId);

    Optional<RunSnapshot> activeRunForSession(String sessionId);

    void emit(String runId, RunEventType type, String payload);

    List<RunEvent> eventsAfter(String runId, long afterIndex);

    void waitForApproval(String runId, RunApproval approval);

    void resolveApproval(String runId, String approvalId, boolean approved);

    void submitBusyInput(String runId, String input, BusyInputMode mode);

    Optional<String> takePendingSteer(String runId);

    Optional<String> takeQueuedInput(String runId);

    void complete(String runId, String output);

    void stop(String runId, String reason);

    void fail(String runId, String error);
}
