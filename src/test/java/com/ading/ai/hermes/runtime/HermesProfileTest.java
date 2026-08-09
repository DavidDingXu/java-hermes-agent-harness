package com.ading.ai.hermes.runtime;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HermesProfileTest {

    @Test
    void keepsDefaultStateCompatibleAndIsolatesNamedProfiles() {
        Path workspace = Path.of("workspace").toAbsolutePath();

        assertEquals(
                workspace.resolve(".hermes"),
                HermesProfile.defaultProfile().stateDirectory(workspace)
        );
        assertEquals(
                workspace.resolve(".hermes/profiles/reviewer"),
                new HermesProfile("reviewer").stateDirectory(workspace)
        );
    }

    @Test
    void rejectsNamesThatCouldEscapeTheProfileDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new HermesProfile("../shared"));
    }
}
