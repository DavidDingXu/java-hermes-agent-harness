package com.dingxu.ai.hermes.terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProcessTerminalBackendTest {

    @TempDir
    Path workspace;

    @Test
    void executesArgvWithoutDependingOnAShell() {
        LocalProcessTerminalBackend backend = new LocalProcessTerminalBackend(workspace, Set.of());

        TerminalResult result = backend.execute(new TerminalCommand(
                List.of(javaBinary(), "-version"),
                Path.of("."),
                Map.of(),
                Duration.ofSeconds(5),
                10_000
        ));

        assertEquals(TerminalStatus.SUCCESS, result.status());
        assertEquals(0, result.exitCode());
        assertTrue(result.output().toLowerCase().contains("version"));
        assertFalse(result.truncated());
    }

    @Test
    void passesOnlyExplicitlyAllowedEnvironmentVariables() {
        LocalProcessTerminalBackend backend = new LocalProcessTerminalBackend(
                workspace, Set.of("SAFE_VALUE")
        );
        String classpath = System.getProperty("java.class.path");

        TerminalResult result = backend.execute(new TerminalCommand(
                List.of(javaBinary(), "-cp", classpath, EnvironmentEchoMain.class.getName(),
                        "SAFE_VALUE", "OPENAI_API_KEY"),
                Path.of("."),
                Map.of("SAFE_VALUE", "visible", "OPENAI_API_KEY", "secret"),
                Duration.ofSeconds(5),
                10_000
        ));

        assertEquals(TerminalStatus.SUCCESS, result.status());
        assertEquals("visible|null", result.output());
    }

    @Test
    void rejectsWorkingDirectoriesOutsideWorkspace() {
        LocalProcessTerminalBackend backend = new LocalProcessTerminalBackend(workspace, Set.of());

        assertThrows(IllegalArgumentException.class, () -> backend.execute(new TerminalCommand(
                List.of(javaBinary(), "-version"),
                Path.of(".."),
                Map.of(),
                Duration.ofSeconds(5),
                10_000
        )));
    }

    @Test
    void rejectsWorkingDirectoriesReachedThroughASymlink() throws Exception {
        Path outside = Files.createTempDirectory(workspace.getParent(), "outside-");
        try {
            Files.createSymbolicLink(workspace.resolve("linked"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException error) {
            return;
        }
        LocalProcessTerminalBackend backend = new LocalProcessTerminalBackend(workspace, Set.of());

        assertThrows(IllegalArgumentException.class, () -> backend.execute(new TerminalCommand(
                List.of(javaBinary(), "-version"),
                Path.of("linked"),
                Map.of(),
                Duration.ofSeconds(5),
                10_000
        )));
    }

    @Test
    void boundsLargeOutputWhileKeepingUsefulHeadAndTail() {
        LocalProcessTerminalBackend backend = new LocalProcessTerminalBackend(workspace, Set.of());

        TerminalResult result = backend.execute(javaTestCommand("output", "200000", Duration.ofSeconds(5), 1_000));

        assertEquals(TerminalStatus.SUCCESS, result.status());
        assertTrue(result.truncated());
        assertTrue(result.output().startsWith("HEAD-"));
        assertTrue(result.output().endsWith("-TAIL"));
        assertTrue(result.output().length() <= 1_000);
    }

    @Test
    void stopsProcessWhenTimeoutExpires() {
        LocalProcessTerminalBackend backend = new LocalProcessTerminalBackend(workspace, Set.of());

        TerminalResult result = backend.execute(javaTestCommand("sleep", "5000", Duration.ofMillis(100), 1_000));

        assertEquals(TerminalStatus.TIMEOUT, result.status());
        assertEquals(-1, result.exitCode());
        assertTrue(result.output().contains("timed out"));
    }

    @Test
    void destroysChildProcessWhenCallingThreadIsInterrupted() throws Exception {
        LocalProcessTerminalBackend backend = new LocalProcessTerminalBackend(workspace, Set.of());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> backend.execute(
                    javaTestCommand("sleep", "5000", Duration.ofSeconds(10), 1_000)
            ));
            Thread.sleep(150);
            future.cancel(true);

            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    private TerminalCommand javaTestCommand(
            String mode,
            String value,
            Duration timeout,
            int maxOutputCharacters
    ) {
        return new TerminalCommand(
                List.of(
                        javaBinary(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        TerminalTestMain.class.getName(),
                        mode,
                        value
                ),
                Path.of("."),
                Map.of(),
                timeout,
                maxOutputCharacters
        );
    }

    private static String javaBinary() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
