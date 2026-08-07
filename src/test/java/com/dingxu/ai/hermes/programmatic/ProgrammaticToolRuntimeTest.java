package com.dingxu.ai.hermes.programmatic;

import com.dingxu.ai.hermes.tool.ToolDefinition;
import com.dingxu.ai.hermes.tool.ToolRegistry;
import com.dingxu.ai.hermes.tool.ToolResult;
import com.dingxu.ai.hermes.tool.ToolSchema;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgrammaticToolRuntimeTest {

    @Test
    void keepsIntermediateToolResultsInsideTheProgram() {
        ProgrammaticToolRuntime runtime = new ProgrammaticToolRuntime(registry());
        ProgrammaticToolRequest request = new ProgrammaticToolRequest(
                "sum-three",
                context -> {
                    int total = 0;
                    for (int value : java.util.List.of(1, 2, 3)) {
                        String content = context.call("number", Map.of("value", value)).content();
                        total += Integer.parseInt(content);
                    }
                    return "total=" + total;
                },
                Set.of("number"),
                5,
                Duration.ofSeconds(1)
        );

        ProgrammaticToolResult result = runtime.execute(request);

        assertEquals(ProgrammaticToolStatus.SUCCESS, result.status());
        assertEquals("total=6", result.output());
        assertEquals(3, result.toolCalls());
    }

    @Test
    void deniesToolsOutsideTheProgramAllowlist() {
        ProgrammaticToolRuntime runtime = new ProgrammaticToolRuntime(registry());

        ProgrammaticToolResult result = runtime.execute(new ProgrammaticToolRequest(
                "blocked",
                context -> context.call("number", Map.of("value", 1)).content(),
                Set.of("read_file"),
                5,
                Duration.ofSeconds(1)
        ));

        assertEquals(ProgrammaticToolStatus.BLOCKED, result.status());
        assertEquals(0, result.toolCalls());
    }

    @Test
    void stopsProgramsThatExceedTheToolCallBudget() {
        ProgrammaticToolRuntime runtime = new ProgrammaticToolRuntime(registry());

        ProgrammaticToolResult result = runtime.execute(new ProgrammaticToolRequest(
                "too-many",
                context -> {
                    context.call("number", Map.of("value", 1));
                    context.call("number", Map.of("value", 2));
                    return "unreachable";
                },
                Set.of("number"),
                1,
                Duration.ofSeconds(1)
        ));

        assertEquals(ProgrammaticToolStatus.BUDGET_EXCEEDED, result.status());
        assertEquals(1, result.toolCalls());
    }

    private static ToolRegistry registry() {
        return ToolRegistry.empty().register(new ToolDefinition(
                "number", "return a number", ToolSchema.object(),
                request -> ToolResult.success(request.callId(), request.arguments().get("value").toString())
        ));
    }
}
