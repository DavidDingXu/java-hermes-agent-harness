package com.ading.ai.hermes.programmatic;

import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.tool.ToolRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProgrammaticToolRuntime {

    private static final int MAX_OUTPUT_CHARACTERS = 50_000;
    private static final String TRUNCATION_MARKER = "\n... [output truncated] ...\n";

    private final ToolRegistry registry;

    public ProgrammaticToolRuntime(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public ProgrammaticToolResult execute(ProgrammaticToolRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AtomicInteger toolCalls = new AtomicInteger();
        ProgrammaticToolContext context = (toolName, arguments) -> {
            if (!request.allowedTools().contains(toolName)) {
                throw new ProgramFailure(
                        ProgrammaticToolStatus.BLOCKED,
                        "tool is not allowed in program: " + toolName
                );
            }
            int current = toolCalls.get();
            if (current >= request.maxToolCalls()) {
                throw new ProgramFailure(
                        ProgrammaticToolStatus.BUDGET_EXCEEDED,
                        "program exceeded tool call budget " + request.maxToolCalls()
                );
            }
            int callNumber = toolCalls.incrementAndGet();
            return registry.execute(new ToolRequest(
                    request.programName() + "-" + callNumber,
                    toolName,
                    Map.copyOf(arguments)
            ));
        };

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> request.program().run(context));
            try {
                String output = future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
                return new ProgrammaticToolResult(
                        ProgrammaticToolStatus.SUCCESS,
                        limit(output == null ? "" : output),
                        toolCalls.get()
                );
            } catch (TimeoutException exception) {
                future.cancel(true);
                return new ProgrammaticToolResult(
                        ProgrammaticToolStatus.TIMEOUT,
                        "program timed out after " + request.timeout(),
                        toolCalls.get()
                );
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof ProgramFailure failure) {
                    return new ProgrammaticToolResult(
                            failure.status,
                            failure.getMessage(),
                            toolCalls.get()
                    );
                }
                return new ProgrammaticToolResult(
                        ProgrammaticToolStatus.FAILED,
                        limit(cause.getClass().getSimpleName() + ": " + cause.getMessage()),
                        toolCalls.get()
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return new ProgrammaticToolResult(
                        ProgrammaticToolStatus.FAILED,
                        "program execution interrupted",
                        toolCalls.get()
                );
            }
        }
    }

    private static String limit(String output) {
        if (output.length() <= MAX_OUTPUT_CHARACTERS) {
            return output;
        }
        int retainedCharacters = MAX_OUTPUT_CHARACTERS - TRUNCATION_MARKER.length();
        int headCharacters = (retainedCharacters + 1) / 2;
        int tailCharacters = retainedCharacters / 2;
        return output.substring(0, headCharacters)
                + TRUNCATION_MARKER
                + output.substring(output.length() - tailCharacters);
    }

    private static final class ProgramFailure extends RuntimeException {
        private final ProgrammaticToolStatus status;

        private ProgramFailure(ProgrammaticToolStatus status, String message) {
            super(message);
            this.status = status;
        }
    }
}
