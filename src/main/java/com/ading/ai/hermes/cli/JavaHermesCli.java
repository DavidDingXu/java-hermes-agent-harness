package com.ading.ai.hermes.cli;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.observability.TraceRedactor;
import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class JavaHermesCli {

    private static final int DEFAULT_MAX_TURNS = 8;
    private static final TraceRedactor REDACTOR = new TraceRedactor();

    private final AgentRuntime runtime;
    private final PrintStream out;
    private final PrintStream err;

    public JavaHermesCli(AgentRuntime runtime, PrintStream out, PrintStream err) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.err = Objects.requireNonNull(err, "err must not be null");
    }

    public int run(String... args) {
        if (isHelpRequest(args)) {
            printUsage(out);
            return 0;
        }
        CliArguments parsed;
        try {
            parsed = CliArguments.parse(args);
        } catch (IllegalArgumentException error) {
            err.println(error.getMessage());
            printUsage(err);
            return 2;
        }
        if (parsed.prompt().isBlank()) {
            err.println("--prompt is required");
            printUsage(err);
            return 2;
        }

        try {
            var result = runtime.run(AgentRunRequest.from(
                    "cli",
                    parsed.sessionId(),
                    parsed.prompt(),
                    IterationBudget.maxTurns(parsed.maxTurns()),
                    Map.of()
            ));
            out.println("sessionId=" + parsed.sessionId());
            printExecutionSummary(result.state().events());
            out.println(result.finalAnswer());
            out.println("finishReason=" + result.finishReason() + " turnsUsed=" + result.state().turnsUsed());
            return result.finishReason() == com.ading.ai.hermes.core.FinishReason.FINAL_ANSWER ? 0 : 1;
        } catch (RuntimeException error) {
            err.println("agent run failed: " + error.getMessage());
            return 1;
        }
    }

    public static boolean isHelpRequest(String... args) {
        if (args == null) {
            return false;
        }
        for (String argument : args) {
            if ("--help".equals(argument) || "-h".equals(argument)) {
                return true;
            }
        }
        return false;
    }

    static void printUsage(PrintStream stream) {
        stream.println("Usage: java-hermes-agent-harness --prompt <task> "
                + "[--max-turns <number>] [--session <id>]");
    }

    private void printExecutionSummary(java.util.List<AgentEvent> events) {
        for (AgentEvent event : events) {
            switch (event.kind()) {
                case TOOL_REQUESTED -> out.println("toolRequest="
                        + event.toolRequest().name()
                        + " callId="
                        + event.toolRequest().callId());
                case TOOL_OBSERVED -> out.println("toolObservation="
                        + event.toolObservation().callId()
                        + " success="
                        + event.toolObservation().success());
                case ERROR_RECOVERED, COMPLETION_REJECTED, RUN_INTERRUPTED -> out.println("runtimeEvent="
                        + event.kind()
                        + " detail="
                        + REDACTOR.redact(event.text()));
                case USER_MESSAGE, CONTEXT_SUMMARY, MODEL_FINAL_ANSWER -> {
                    // Final text is printed once below; arguments and content stay out of terminal logs.
                }
            }
        }
    }

    private record CliArguments(String prompt, int maxTurns, String sessionId) {

        private static CliArguments parse(String[] args) {
            String prompt = "";
            int maxTurns = DEFAULT_MAX_TURNS;
            String sessionId = "cli-" + UUID.randomUUID();
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if ("--prompt".equals(argument)) {
                    prompt = value(args, ++index, argument);
                } else if ("--max-turns".equals(argument)) {
                    String raw = value(args, ++index, argument);
                    try {
                        maxTurns = Integer.parseInt(raw);
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException("--max-turns must be an integer");
                    }
                    if (maxTurns < 1) {
                        throw new IllegalArgumentException("--max-turns must be at least 1");
                    }
                } else if ("--session".equals(argument)) {
                    sessionId = value(args, ++index, argument).trim();
                    if (!sessionId.matches("[A-Za-z0-9._-]+")) {
                        throw new IllegalArgumentException(
                                "--session may contain only letters, numbers, dot, dash or underscore"
                        );
                    }
                } else {
                    throw new IllegalArgumentException("unknown argument: " + argument);
                }
            }
            return new CliArguments(prompt, maxTurns, sessionId);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}
