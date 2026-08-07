package com.ading.ai.hermes.plugin;

import com.ading.ai.hermes.hook.HookFailureMode;
import com.ading.ai.hermes.hook.RuntimeHook;
import com.ading.ai.hermes.hook.RuntimeHookPoint;
import com.ading.ai.hermes.tool.ToolDefinition;

public interface PluginContext {

    void registerTool(String toolset, ToolDefinition definition);

    void registerHook(
            String name,
            RuntimeHookPoint point,
            int priority,
            HookFailureMode failureMode,
            RuntimeHook hook
    );
}
