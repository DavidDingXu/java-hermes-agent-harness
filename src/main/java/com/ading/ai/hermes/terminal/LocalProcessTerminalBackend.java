package com.ading.ai.hermes.terminal;

import com.ading.ai.hermes.workspace.WorkspacePathPolicy;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class LocalProcessTerminalBackend implements TerminalBackend {

    private static final Set<String> POSIX_ESSENTIAL_ENV = Set.of(
            "PATH", "LANG", "LC_ALL", "TMPDIR"
    );
    private static final Set<String> WINDOWS_ESSENTIAL_ENV = Set.of(
            "PATH", "SystemRoot", "SYSTEMROOT", "WINDIR", "ComSpec", "COMSPEC", "TEMP", "TMP"
    );

    private final Path workspace;
    private final WorkspacePathPolicy pathPolicy;
    private final Set<String> allowedEnvironment;

    public LocalProcessTerminalBackend(Path workspace, Set<String> allowedEnvironment) {
        this.pathPolicy = new WorkspacePathPolicy(workspace);
        this.workspace = pathPolicy.root();
        this.allowedEnvironment = Set.copyOf(allowedEnvironment);
    }

    @Override
    public TerminalResult execute(TerminalCommand command) {
        Path workingDirectory = pathPolicy.resolveExisting(command.workingDirectory().toString());
        if (!Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("terminal working directory does not exist");
        }

        ProcessBuilder builder = new ProcessBuilder(command.argv())
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        Map<String, String> processEnvironment = builder.environment();
        processEnvironment.clear();
        processEnvironment.putAll(safeBaseEnvironment(System.getenv()));
        for (Map.Entry<String, String> entry : command.environment().entrySet()) {
            if (allowedEnvironment.contains(entry.getKey())) {
                processEnvironment.put(entry.getKey(), entry.getValue());
            }
        }

        try {
            Process process = builder.start();
            try (ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor()) {
                try {
                    Future<BoundedOutput> output = reader.submit(() -> BoundedOutput.read(
                            process.getInputStream(), command.maxOutputCharacters()
                    ));
                    boolean finished = process.waitFor(command.timeout().toMillis(), TimeUnit.MILLISECONDS);
                    if (!finished) {
                        destroyProcessTree(process);
                        return render(
                                TerminalStatus.TIMEOUT,
                                output.get(2, TimeUnit.SECONDS),
                                -1,
                                command.maxOutputCharacters(),
                                "\n[command timed out]"
                        );
                    }
                    return render(
                            process.exitValue() == 0 ? TerminalStatus.SUCCESS : TerminalStatus.FAILED,
                            output.get(2, TimeUnit.SECONDS),
                            process.exitValue(),
                            command.maxOutputCharacters(),
                            ""
                    );
                } catch (InterruptedException exception) {
                    destroyProcessTree(process);
                    throw exception;
                }
            }
        } catch (IOException exception) {
            return new TerminalResult(
                    TerminalStatus.FAILED,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    -1,
                    false
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new TerminalResult(TerminalStatus.FAILED, "terminal execution interrupted", -1, false);
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            return new TerminalResult(
                    TerminalStatus.FAILED,
                    "failed to collect terminal output: " + exception.getMessage(),
                    -1,
                    false
            );
        }
    }

    private Map<String, String> safeBaseEnvironment(Map<String, String> inherited) {
        Set<String> essential = isWindows() ? WINDOWS_ESSENTIAL_ENV : POSIX_ESSENTIAL_ENV;
        Map<String, String> safe = new LinkedHashMap<>();
        for (String name : essential) {
            String value = inherited.get(name);
            if (value != null) {
                safe.put(name, value);
            }
        }
        for (String name : allowedEnvironment) {
            String value = inherited.get(name);
            if (value != null) {
                safe.put(name, value);
            }
        }
        return safe;
    }

    private static TerminalResult render(
            TerminalStatus status,
            BoundedOutput captured,
            int exitCode,
            int maxCharacters,
            String suffix
    ) {
        RenderedOutput output = captured.render(maxCharacters, suffix);
        return new TerminalResult(status, output.text(), exitCode, output.truncated());
    }

    private static void destroyProcessTree(Process process) {
        process.descendants().forEach(handle -> {
            handle.destroy();
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static final class BoundedOutput {

        private static final String TRUNCATION_MARKER = "\n... [terminal output truncated] ...\n";

        private final int headLimit;
        private final int tailLimit;
        private final StringBuilder head;
        private final StringBuilder tail;
        private long characterCount;

        private BoundedOutput(int maxCharacters) {
            headLimit = Math.max(1, maxCharacters * 2 / 5);
            tailLimit = Math.max(0, maxCharacters - headLimit);
            head = new StringBuilder(headLimit);
            tail = new StringBuilder(tailLimit);
        }

        private static BoundedOutput read(java.io.InputStream input, int maxCharacters) throws IOException {
            BoundedOutput output = new BoundedOutput(maxCharacters);
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    output.append(buffer, read);
                }
            }
            return output;
        }

        private void append(char[] chars, int length) {
            characterCount += length;
            int offset = 0;
            int headRemaining = headLimit - head.length();
            if (headRemaining > 0) {
                int copied = Math.min(headRemaining, length);
                head.append(chars, 0, copied);
                offset = copied;
            }
            if (offset < length && tailLimit > 0) {
                tail.append(chars, offset, length - offset);
                if (tail.length() > tailLimit) {
                    tail.delete(0, tail.length() - tailLimit);
                }
            }
        }

        private RenderedOutput render(int maxCharacters, String suffix) {
            String raw = characterCount <= head.length() + tail.length()
                    ? head.toString() + tail
                    : null;
            if (raw != null && raw.length() + suffix.length() <= maxCharacters) {
                return new RenderedOutput(raw + suffix, false);
            }

            String marker = TRUNCATION_MARKER.substring(
                    0, Math.min(TRUNCATION_MARKER.length(), maxCharacters)
            );
            int contentBudget = maxCharacters - marker.length();
            int headBudget = contentBudget * 2 / 5;
            int tailBudget = contentBudget - headBudget;
            String tailWithSuffix = tail + suffix;
            String visibleHead = head.substring(0, Math.min(headBudget, head.length()));
            String visibleTail = tailWithSuffix.substring(Math.max(0, tailWithSuffix.length() - tailBudget));
            return new RenderedOutput(visibleHead + marker + visibleTail, true);
        }
    }

    private record RenderedOutput(String text, boolean truncated) {
    }
}
