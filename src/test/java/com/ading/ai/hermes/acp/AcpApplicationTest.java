package com.ading.ai.hermes.acp;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcpApplicationTest {

    @TempDir
    Path launchDirectory;

    @Test
    void acceptsAnExplicitConfigurationFileForClientManagedLaunches() {
        Path expected = launchDirectory.resolve("private/hermes.properties").toAbsolutePath();

        AcpLaunchOptions options = AcpLaunchOptions.parse(
                new String[]{"--config", expected.toString()},
                launchDirectory
        );

        assertEquals(expected.normalize(), options.configurationFile());
    }

    @Test
    void resolvesRelativeConfigurationPathsFromTheLaunchDirectory() {
        AcpLaunchOptions options = AcpLaunchOptions.parse(
                new String[]{"--config", "private/hermes.properties"},
                launchDirectory
        );

        assertEquals(
                launchDirectory.resolve("private/hermes.properties").toAbsolutePath().normalize(),
                options.configurationFile()
        );
    }

    @Test
    void rejectsUnknownOrIncompleteArgumentsBeforeStartingTheTransport() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcpLaunchOptions.parse(new String[]{"--workspace", "demo"}, launchDirectory)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AcpLaunchOptions.parse(new String[]{"--config"}, launchDirectory)
        );
    }
}
