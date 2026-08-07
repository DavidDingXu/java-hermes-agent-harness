package com.ading.ai.hermes.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTrajectoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsTrajectoryRecordAsJsonLine() throws Exception {
        Path file = tempDir.resolve("trajectory.jsonl");
        FileTrajectoryStore store = new FileTrajectoryStore(file, new ObjectMapper());
        TrajectoryRecord record = new TrajectoryRecord(
                "session-1",
                "turn-1",
                "2026-06-20T10:15:30Z",
                List.of(new TraceEvent(
                        TraceEventKind.RUN_FINISHED,
                        "session-1",
                        "turn-1",
                        "",
                        "",
                        "2026-06-20T10:15:30Z",
                        Map.of("finishReason", "FINAL_ANSWER")
                ))
        );

        store.append(record);

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        Map<?, ?> json = new ObjectMapper().readValue(lines.get(0), Map.class);
        assertEquals("session-1", json.get("sessionId"));
        assertEquals("turn-1", json.get("turnId"));
    }
}
