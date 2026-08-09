package com.ading.ai.hermes.context.reference;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class ProcessGitContextReader implements GitContextReader {

    private final Path workspace;
    private final Duration timeout;

    public ProcessGitContextReader(Path workspace) {
        this(workspace, Duration.ofSeconds(10));
    }

    public ProcessGitContextReader(Path workspace, Duration timeout) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.timeout = timeout;
    }

    @Override
    public String read(String reference) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        if ("diff".equals(reference)) {
            command.add("diff");
        } else if ("staged".equals(reference)) {
            command.addAll(List.of("diff", "--staged"));
        } else if (reference.startsWith("git:")) {
            int count = Math.max(1, Math.min(Integer.parseInt(reference.substring(4)), 10));
            command.addAll(List.of("log", "-" + count, "-p"));
        } else {
            throw new IllegalArgumentException("unsupported git reference: " + reference);
        }

        Process process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("git context command timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git context command failed: " + output.strip());
        }
        return output;
    }
}
