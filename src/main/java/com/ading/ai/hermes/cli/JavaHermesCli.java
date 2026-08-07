package com.ading.ai.hermes.cli;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.IterationBudget;
import java.io.PrintStream;
import java.util.Objects;

public final class JavaHermesCli {

    private static final int DEFAULT_MAX_TURNS = 8;

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
            var result = runtime.run(AgentRunRequest.start(
                    parsed.prompt(),
                    IterationBudget.maxTurns(parsed.maxTurns())
            ));
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
        stream.println("Usage: java-hermes-agent-harness --prompt <task> [--max-turns <number>]");
    }

    private record CliArguments(String prompt, int maxTurns) {

        private static CliArguments parse(String[] args) {
            String prompt = "";
            int maxTurns = DEFAULT_MAX_TURNS;
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
                } else {
                    throw new IllegalArgumentException("unknown argument: " + argument);
                }
            }
            return new CliArguments(prompt, maxTurns);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}
