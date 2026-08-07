package com.dingxu.ai.hermes.plugin;

import com.dingxu.ai.hermes.hook.HookFailureMode;
import com.dingxu.ai.hermes.hook.RuntimeHook;
import com.dingxu.ai.hermes.hook.RuntimeHookChain;
import com.dingxu.ai.hermes.hook.RuntimeHookPoint;
import com.dingxu.ai.hermes.tool.ToolDefinition;
import com.dingxu.ai.hermes.toolset.ToolsetCatalog;
import java.util.ArrayList;
import java.util.List;

public final class PluginHost {

    private final ToolsetCatalog toolsets;
    private final RuntimeHookChain hooks;

    private PluginHost(ToolsetCatalog toolsets, RuntimeHookChain hooks) {
        this.toolsets = toolsets;
        this.hooks = hooks;
    }

    public static PluginHost empty() {
        return new PluginHost(ToolsetCatalog.empty(), RuntimeHookChain.empty());
    }

    public PluginHost install(RuntimePlugin plugin) {
        CollectingPluginContext context = new CollectingPluginContext();
        plugin.register(context);

        ToolsetCatalog nextToolsets = toolsets;
        for (ToolRegistration registration : context.tools) {
            nextToolsets = nextToolsets.register(registration.toolset(), registration.definition());
        }

        RuntimeHookChain nextHooks = hooks;
        for (HookRegistration registration : context.hooks) {
            nextHooks = nextHooks.register(
                    registration.name(),
                    registration.point(),
                    registration.priority(),
                    registration.failureMode(),
                    registration.hook()
            );
        }
        return new PluginHost(nextToolsets, nextHooks);
    }

    public ToolsetCatalog toolsets() {
        return toolsets;
    }

    public RuntimeHookChain hooks() {
        return hooks;
    }

    private static final class CollectingPluginContext implements PluginContext {
        private final List<ToolRegistration> tools = new ArrayList<>();
        private final List<HookRegistration> hooks = new ArrayList<>();

        @Override
        public void registerTool(String toolset, ToolDefinition definition) {
            tools.add(new ToolRegistration(toolset, definition));
        }

        @Override
        public void registerHook(
                String name,
                RuntimeHookPoint point,
                int priority,
                HookFailureMode failureMode,
                RuntimeHook hook
        ) {
            hooks.add(new HookRegistration(name, point, priority, failureMode, hook));
        }
    }

    private record ToolRegistration(String toolset, ToolDefinition definition) {
    }

    private record HookRegistration(
            String name,
            RuntimeHookPoint point,
            int priority,
            HookFailureMode failureMode,
            RuntimeHook hook
    ) {
    }
}
