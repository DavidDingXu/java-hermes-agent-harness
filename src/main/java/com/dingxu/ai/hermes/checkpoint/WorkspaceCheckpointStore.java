package com.dingxu.ai.hermes.checkpoint;

import java.io.IOException;
import java.util.List;

public interface WorkspaceCheckpointStore {

    WorkspaceCheckpoint capture(List<String> relativePaths) throws IOException;

    List<WorkspaceChange> diff(String checkpointId) throws IOException;

    WorkspaceRollbackResult rollback(String checkpointId) throws IOException;
}
