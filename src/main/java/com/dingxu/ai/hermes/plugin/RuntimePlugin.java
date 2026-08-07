package com.dingxu.ai.hermes.plugin;

@FunctionalInterface
public interface RuntimePlugin {

    void register(PluginContext context);
}
