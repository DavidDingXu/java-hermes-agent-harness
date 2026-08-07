package com.dingxu.ai.hermes.session;

import java.util.Objects;

public final class RunCheckpointPlanner {

    private final SessionRestorer sessionRestorer;

    public RunCheckpointPlanner(SessionRestorer sessionRestorer) {
        this.sessionRestorer = Objects.requireNonNull(sessionRestorer, "sessionRestorer must not be null");
    }

    public RunCheckpoint plan(SessionRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        RestoredSession restored = sessionRestorer.restore(record);
        return new RunCheckpoint(
                record.sessionId(),
                record.events().size() - 1,
                restored.state(),
                restored.decision(),
                restored.pendingToolRequests()
        );
    }
}
