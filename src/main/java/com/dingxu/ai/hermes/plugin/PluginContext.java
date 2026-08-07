package com.dingxu.ai.hermes.plugin;

import com.dingxu.ai.hermes.hook.HookFailureMode;
import com.dingxu.ai.hermes.hook.RuntimeHook;
import com.dingxu.ai.hermes.hook.RuntimeHookPoint;
import com.dingxu.ai.hermes.tool.ToolDefinition;

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
