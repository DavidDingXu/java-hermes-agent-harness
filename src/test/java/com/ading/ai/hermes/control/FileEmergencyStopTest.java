package com.ading.ai.hermes.control;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileEmergencyStopTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void pausesNewWorkUntilTheSentinelIsRemoved() {
        FileEmergencyStop emergencyStop = new FileEmergencyStop(
                temporaryDirectory.resolve("runtime/ESTOP")
        );

        emergencyStop.engage("maintenance");

        assertTrue(emergencyStop.engaged());
        assertFalse(emergencyStop.evaluate().allowed());
        assertEquals("new work is paused: maintenance", emergencyStop.evaluate().reason());
        assertEquals("maintenance", emergencyStop.state().orElseThrow().reason());

        assertTrue(emergencyStop.resume());
        assertTrue(emergencyStop.evaluate().allowed());
        assertFalse(emergencyStop.resume());
    }

    @Test
    void treatsACorruptSentinelAsAnEngagedStop() throws Exception {
        Path sentinel = temporaryDirectory.resolve("ESTOP");
        Files.writeString(sentinel, "not-json");
        FileEmergencyStop emergencyStop = new FileEmergencyStop(sentinel);

        assertFalse(emergencyStop.evaluate().allowed());
        assertEquals(
                "new work is paused by the emergency stop",
                emergencyStop.evaluate().reason()
        );
    }
}
