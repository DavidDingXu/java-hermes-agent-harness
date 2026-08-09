package com.ading.ai.hermes.plugin;

@FunctionalInterface
public interface RuntimePlugin {

    void register(PluginContext context);
}
