package com.ading.ai.hermes.plugin;

import com.ading.ai.hermes.hook.HookFailureMode;
import com.ading.ai.hermes.hook.RuntimeHookDecision;
import com.ading.ai.hermes.hook.RuntimeHookEvent;
import com.ading.ai.hermes.hook.RuntimeHookPoint;
import com.ading.ai.hermes.tool.ToolDefinition;
import com.ading.ai.hermes.tool.ToolResult;
import com.ading.ai.hermes.tool.ToolSchema;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginHostTest {

    @Test
    void pluginRegistersToolsAndTransformsWithoutChangingTheMainLoop() {
        RuntimePlugin plugin = context -> {
            context.registerTool("review", new ToolDefinition(
                    "lint", "lint source", ToolSchema.object(),
                    request -> ToolResult.success(request.callId(), "clean")
            ));
            context.registerHook(
                    "canonical-result",
                    RuntimeHookPoint.TRANSFORM_TOOL_RESULT,
                    10,
                    HookFailureMode.FAIL_OPEN,
                    event -> RuntimeHookDecision.continueWith(Map.of(
                            "result", event.payload().get("result").toString().toUpperCase()
                    ))
            );
        };

        PluginHost host = PluginHost.empty().install(plugin);
        RuntimeHookDecision decision = host.hooks().invoke(new RuntimeHookEvent(
                RuntimeHookPoint.TRANSFORM_TOOL_RESULT,
                "run-1",
                "lint",
                Map.of("result", "clean")
        ));

        assertEquals("CLEAN", decision.payload().get("result"));
        assertEquals(1, host.toolsets().select(Set.of("review")).specs().size());
    }

    @Test
    void observationalHookFailureIsVisibleButDoesNotStopTheRun() {
        PluginHost host = PluginHost.empty().install(context -> context.registerHook(
                "metrics",
                RuntimeHookPoint.AFTER_TOOL,
                10,
                HookFailureMode.FAIL_OPEN,
                event -> { throw new IllegalStateException("metrics offline"); }
        ));

        RuntimeHookDecision decision = host.hooks().invoke(new RuntimeHookEvent(
                RuntimeHookPoint.AFTER_TOOL, "run-1", "read_file", Map.of()
        ));

        assertTrue(decision.allowed());
        assertTrue(decision.warnings().getFirst().contains("metrics offline"));
    }

    @Test
    void securityHookFailureBlocksTheRun() {
        PluginHost host = PluginHost.empty().install(context -> context.registerHook(
                "policy",
                RuntimeHookPoint.BEFORE_TOOL,
                10,
                HookFailureMode.FAIL_CLOSED,
                event -> { throw new IllegalStateException("policy unavailable"); }
        ));

        RuntimeHookDecision decision = host.hooks().invoke(new RuntimeHookEvent(
                RuntimeHookPoint.BEFORE_TOOL, "run-1", "terminal", Map.of("command", "deploy")
        ));

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("policy unavailable"));
    }
}
